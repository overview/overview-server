package models.orm.stores

import java.sql.Timestamp
import java.util.Date
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.SearchResultState._
import org.overviewproject.postgres.LO
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }
import org.overviewproject.tree.{ DocumentSetCreationJobType, Ownership }
import helpers.{DbTestContext, PgConnectionContext}
import models.Selection
import models.orm._
import models.orm.finders._

class DocumentSetStoreSpec extends Specification {

  trait DocumentSetContext extends PgConnectionContext {
    private val documentStore = new BaseStore(models.orm.Schema.documents) with NoInsertOrUpdate[Document]
    
    def insertDocumentSet = {
      DocumentSetStore.insertOrUpdate(DocumentSet(title="title", query=Some("query")))
    }

    def insertUploadedFileAndDocumentSet = {
      val uploadedFile = UploadedFileStore.insertOrUpdate(UploadedFile(contentDisposition = "content-disposition", contentType = "content-type", size = 100))
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(title="title", uploadedFileId = Some(uploadedFile.id)))
      (uploadedFile, documentSet)
    }
  }

  step(start(FakeApplication()))

  "DocumentSetStore" should {
    "set createdAt to the current date by default" in new Scope {
      DocumentSet().createdAt.getTime must beCloseTo((new Date().getTime), 1000)
    }

    "set isPublic to false by default" in new DocumentSetContext {
      DocumentSet().isPublic must beFalse
    }

    "set deleted to false by default" in new DocumentSetContext {
      DocumentSet().deleted must beFalse
    }
    
    "delete document_set_creation_job entries" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        jobType=DocumentSetCreationJobType.DocumentCloud,
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.NotStarted
      ))

      DocumentSetStore.deleteOrCancelJob(documentSet)

      DocumentSetCreationJobFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }


    "set document set deleted flag to true on delete" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      
      DocumentSetStore.markDeleted(documentSet)
      
      val deletedDocumentSet = DocumentSetFinder.byDocumentSet(documentSet.id).headOption
      
      deletedDocumentSet must beSome.like { case ds => ds.deleted must beTrue }
    }
    
    "delete an uploaded LargeObject when there is a NotStarted job" in new DocumentSetContext {
      val (uploadedFile, documentSet) = insertUploadedFileAndDocumentSet

      val oid = LO.withLargeObject { largeObject =>
        DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
          documentSetId=documentSet.id,
          jobType=DocumentSetCreationJobType.CsvUpload,
          state=DocumentSetCreationJobState.NotStarted,
          contentsOid=Some(largeObject.oid)
        ))
        largeObject.oid
      }

      DocumentSetStore.deleteOrCancelJob(documentSet.id)

      LO.withLargeObject(oid) { lo => } must throwA[java.sql.SQLException]
    }
    
    
    "cancel in-progress clone jobs when deleting a document set" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val cloneDocumentSet = CloneImportJobStore.insertCloneOf(documentSet)
      val cloneJob = DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        documentSetId=cloneDocumentSet.id,
        jobType=DocumentSetCreationJobType.Clone,
        state=DocumentSetCreationJobState.InProgress,
        sourceDocumentSetId=Some(documentSet.id)
      ))

      DocumentSetStore.deleteOrCancelJob(documentSet.id)

      val cancelledJob = DocumentSetCreationJobFinder.byDocumentSet(cloneDocumentSet).headOption
      cancelledJob must beSome like { case Some(job) => job.state must beEqualTo(DocumentSetCreationJobState.Cancelled) }
    }

    "cancel job if in progress" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val job = DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        documentSetId=documentSet.id,
        jobType=DocumentSetCreationJobType.DocumentCloud,
        state=DocumentSetCreationJobState.InProgress
      ))

      DocumentSetStore.deleteOrCancelJob(documentSet)

      // The job must exist, cancelled
      val cancelledJob = DocumentSetCreationJobFinder.byDocumentSet(documentSet).headOption
      cancelledJob must beSome like { case Some(job) => job.state must beEqualTo(DocumentSetCreationJobState.Cancelled) }
    }
  }

  step(stop)
}
