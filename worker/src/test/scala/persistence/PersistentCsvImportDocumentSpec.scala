package persistence

import anorm._
import anorm.SqlParser._
import helpers.DbSpecification
import testutil.DbSetup._

class PersistentCsvImportDocumentSpec extends DbSpecification {
  step(setupDb)
  
  "PersistentCsvImportDocument" should {
    "write text to document table" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentCsvImportDocumentSpec")
      val documentText = "some text"
        
      val document = new PersistentCsvImportDocument {
        val text = documentText
      }
      
      val id = document.write(documentSetId)
      val documents = 
        SQL("SELECT id, text FROM document").as(long("id") ~ str("text") map (flatten) *)
        
      documents must haveTheSameElementsAs(Seq((id, documentText)))
    }
  }
  
  step(shutdownDb)
}