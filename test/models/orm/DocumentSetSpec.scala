package models.orm

import anorm.SQL
import anorm.SqlParser.{flatten, scalar}
import anorm.{sqlToSimple, toParameterValue}
import java.sql.Timestamp
import java.util.Date
import org.junit.runner.RunWith
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import org.overviewproject.postgres.LO
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.{ DocumentProcessingError, DocumentSetCreationJob, UploadedFile }
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import helpers.{DbTestContext, PgConnectionContext}
import models.orm.DocumentSetType._

@RunWith(classOf[JUnitRunner])
class DocumentSetSpec extends Specification {

  trait DocumentSetContext extends DbTestContext {
    val query = Some("query")
    val title = "title"

    var documentSet: DocumentSet = _

    override def setupWithDb = {
      documentSet = Schema.documentSets.insert(DocumentSet(DocumentCloudDocumentSet, 0L, title, query))
    }
  }

  def saveUploadedFile(uploadedFile: UploadedFile): UploadedFile = {
    import models.orm.Schema.uploadedFiles

    uploadedFiles.insertOrUpdate(uploadedFile)
  }

  step(start(FakeApplication()))

  "DocumentSet" should {

    inExample("create a DocumentSetCreationJob") in new DocumentSetContext {
      val job = documentSet.createDocumentSetCreationJob()

      val returnedJob: DocumentSetCreationJob = Schema.documentSetCreationJobs.lookup(job.id).get
      returnedJob.documentSetCreationJobType.value must be equalTo(DocumentCloudJob.value)
      returnedJob.documentSetId must beEqualTo(documentSet.id)
    }

    "set createdAt to the current date by default" in new Scope {
      val documentSet = DocumentSet(DocumentCloudDocumentSet, 0L)
      documentSet.createdAt.getTime must beCloseTo((new Date().getTime), 1000)
    }

    "create a job with type CsvImportDocumentSet" in new PgConnectionContext {
      LO.withLargeObject { lo =>
        val uploadedFile =
          saveUploadedFile(UploadedFile(contentDisposition = "", contentType = "", size = 0))
        val documentSet =
          DocumentSet(documentSetType = CsvImportDocumentSet, uploadedFileId = Some(uploadedFile.id))
        documentSet.save must not(throwA[Exception])
        
        val job = documentSet.createDocumentSetCreationJob(contentsOid = Some(lo.oid))
        job.documentSetCreationJobType.value must be equalTo(CsvImportJob.value)
        job.contentsOid.get must be equalTo(lo.oid)
      }
    }

    "throw exception if job creation is attempted before db insertion" in new DbTestContext {
      val query = "query"
      val title = "title"
      val documentSet = new DocumentSet(DocumentCloudDocumentSet, 0L, title, Some(query))

      documentSet.createDocumentSetCreationJob() must throwAn[IllegalArgumentException]
    }

    "delete all references in other tables" in new DocumentSetContext {
      val id = documentSet.id
      val documentSetCreationJob = documentSet.createDocumentSetCreationJob()

      SQL("""
          INSERT INTO document_set_user (document_set_id, user_email, role)
          VALUES ({documentSetId}, 'user@host.com', 2)
          """).on("documentSetId" -> id).executeInsert()

      SQL("""
          INSERT INTO log_entry (document_set_id, user_id, date, component, action, details)
          VALUES ({documentSetId}, {userId}, '2012-08-20 09:12:12', 'component', 'action', 'details')
          """).on("documentSetId" -> id, "userId" -> 1).executeInsert()
      val tagId = insertTag(id, "tag")
      val nodeIds = insertNodes(id, 1)
      val documentIds = insertDocumentsForeachNode(id, nodeIds, 1)
      tagDocuments(tagId, documentIds)

      DocumentSet.delete(documentSet.id)

      def documentSetEntries(documentSetId: Long, table: String): List[Long] = {
        SQL("SELECT id FROM " + table + " WHERE document_set_id = {documentSetId}").
          on("documentSetId" -> id).as(scalar[Long] *)
      }
      def allEntries(table: String): List[(Long, Long)] = {
        SQL("SELECT * FROM " + table).as(scalar[Long] ~ scalar[Long] map (flatten) *)
      }

      val logEntry = documentSetEntries(id, "log_entry")
      logEntry must be empty

      val tagEntry = documentSetEntries(id, "tag")
      tagEntry must be empty

      val documentTagEntries = allEntries("document_tag")
      documentTagEntries must be empty

      val nodeEntry = documentSetEntries(id, "node")
      nodeEntry must be empty

      val documentEntry = documentSetEntries(id, "document")
      documentEntry must be empty

      val nodeDocumentEntries = allEntries("node_document")
      nodeDocumentEntries must be empty

      val documentSetUserEntries = allEntries("document_set_user")
      documentSetUserEntries must be empty

      val documentSetCreationJobEntries = documentSetEntries(id, "document_set_creation_job")
      documentSetCreationJobEntries must be empty

      val documentSetEntries = allEntries("document_set")
      DocumentSet.findById(documentSet.id) must be empty
    }

 	"delete uploadedFile for CsvImportDocumentsets" in new PgConnectionContext {
      val uploadedFile = saveUploadedFile(UploadedFile(contentDisposition = "content-disposition", contentType = "content-type", size = 100))
      val documentSet = DocumentSet(documentSetType = CsvImportDocumentSet, uploadedFileId = Some(uploadedFile.id)).save
      DocumentSet.delete(documentSet.id)
      Schema.uploadedFiles.lookup(uploadedFile.id) must beNone
    }

    "have no errorCount when there are no errors" in new DocumentSetContext {
      documentSet.errorCount must be equalTo (0)
    }

    "provide count of errors when they exist" in new DocumentSetContext {
      val errorCount = 10
      val errors = Seq.tabulate(errorCount)(i => DocumentProcessingError(documentSet.id, "url", "message"))
      Schema.documentProcessingErrors.insert(errors)

      documentSet.errorCount must be equalTo (errorCount)
    }
    
    "Be set to not public by default" in new DocumentSetContext {
      documentSet.isPublic must beFalse
    }
  }

  step(stop)
}
