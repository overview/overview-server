package org.overviewproject.database

import org.overviewproject.test._
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.{ Document, DocumentSet, UploadedFile }
import org.overviewproject.models.tables.{ Documents, DocumentSets, Files, Pages, UploadedFiles }
import java.sql.Timestamp
import scala.concurrent.Future
import org.overviewproject.test.factories.DbFactory

class DocumentSetDeleterSpec extends SlickSpecification {

  "DocumentSetDeleter" should {

    "delete documents, document_set_user, and document_set" in new BasicDocumentSetScope {
      await { deleter.delete(documentSet.id) }

      findDocumentSet(documentSet.id) must beEmpty
    }

    "delete csv uploads" in new CsvUploadScope {
      await { deleter.delete(documentSet.id) }

      findDocumentSet(documentSet.id) must beEmpty
      findUploadedFile must beEmpty
    }

    "delete user added data" in new UserAddedDataScope {
      deleteDocumentSet

      findDocumentSet(documentSet.id) must beEmpty
    }

    "decrement reference count in files and pages for uploaded files" in new FileUploadScope {
      deleteDocumentSet

      fileReferenceCount must beSome(0)

      pageReferenceCounts must contain(0).exactly(10.times)
    }
  }

  trait BasicDocumentSetScope extends DbScope {
    val numberOfDocuments = 10

    val deleter = new TestDocumentSetDeleter
    val documentSet = createDocumentSet

    val documents = Seq.fill(numberOfDocuments)(createDocument)

    factory.documentSetUser(documentSetId = documentSet.id)

    def deleteDocumentSet = await { deleter.delete(documentSet.id) }

    def findDocumentSet(documentSetId: Long)(implicit session: Session): Option[DocumentSet] = {
      val q = DocumentSets.filter(_.id === documentSetId)
      q.firstOption
    }

    def createDocumentSet: DocumentSet = factory.documentSet()
    def createDocument: Document = factory.document(documentSetId = documentSet.id)
  }

  trait FileUploadScope extends BasicDocumentSetScope {
    override def createDocument: Document = {
      val file = factory.file()
      factory.page(fileId = file.id, pageNumber = 1)

      factory.document(documentSetId = documentSet.id, fileId = Some(file.id))
    }

    def fileReferenceCount: Option[Int] = Files.map(_.referenceCount).firstOption
    def pageReferenceCounts: Seq[Int] = Pages.map(_.referenceCount).list
  }

  trait CsvUploadScope extends BasicDocumentSetScope {

    override def createDocumentSet: DocumentSet = {
      val uploadedFile = factory.uploadedFile(size = 100l)
      factory.documentSet(uploadedFileId = Some(uploadedFile.id))
    }

    def findUploadedFile(implicit session: Session): Option[UploadedFile] =
      UploadedFiles.firstOption

  }

  trait UserAddedDataScope extends BasicDocumentSetScope {
    factory.tag(documentSetId = documentSet.id)
    factory.searchResult(documentSetId = documentSet.id)
  }

  class TestDocumentSetDeleter(implicit session: Session) extends DocumentSetDeleter {
    import scala.concurrent.ExecutionContext.Implicits.global

    override def db[A](block: Session => A): Future[A] = Future {
      block(session)
    }

  }

}