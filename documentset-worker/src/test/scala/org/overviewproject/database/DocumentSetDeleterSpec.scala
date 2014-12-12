package org.overviewproject.database

import org.overviewproject.test._
import org.overviewproject.models.tables.{ Documents, DocumentSets, UploadedFiles }
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.{ Document, DocumentSet, UploadedFile }
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
      await { deleter.delete(documentSet.id) }
    }
  }

  trait BasicDocumentSetScope extends DbScope {
    val deleter = new TestDocumentSetDeleter
    val documentSet = createDocumentSet

    val documents = Seq.fill(10)(factory.document(documentSetId = documentSet.id))
    factory.documentSetUser(documentSetId = documentSet.id)

    def findDocumentSet(documentSetId: Long)(implicit session: Session): Option[DocumentSet] = {
      val q = DocumentSets.filter(_.id === documentSetId)
      q.firstOption
    }

    def createDocumentSet: DocumentSet = factory.documentSet()
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