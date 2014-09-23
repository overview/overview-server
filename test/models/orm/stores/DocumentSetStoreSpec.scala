package models.orm.stores

import java.util.Date
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }
import helpers.PgConnectionContext
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
    

    "set document set deleted flag to true on delete" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      
      DocumentSetStore.markDeleted(documentSet)
      
      val deletedDocumentSet = DocumentSetFinder.byDocumentSet(documentSet.id).headOption
      
      deletedDocumentSet must beSome.like { case ds => ds.deleted must beTrue }
    }
  }

  step(stop)
}
