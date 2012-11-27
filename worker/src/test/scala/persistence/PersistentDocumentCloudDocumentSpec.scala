package persistence

import anorm._
import anorm.SqlParser._
import testutil.DbSpecification
import testutil.DbSetup._
import org.specs2.mutable.Specification

class PersistentDocumentCloudDocumentSpec extends DbSpecification {
  step(setupDb)
  
  "PersistentDocumentCloudDocument" should {
   "write title and document_cloud_id to document table" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentDocumentCloudDocument")

      val documentTitle = "title"
      val dcId = "documentCloud-id"
      val document = new PersistentDocumentCloudDocument {
        val title = documentTitle
        val documentCloudId = dcId
      }
      
      val id = document.write(documentSetId)
      val documents =
        SQL("SELECT id, title, documentcloud_id FROM document").
          as(long("id") ~ str("title") ~ str("documentcloud_id") map (flatten) *)

      documents must haveTheSameElementsAs(Seq((id, documentTitle, dcId)))
    }    
  }
  step(shutdownDb)
}