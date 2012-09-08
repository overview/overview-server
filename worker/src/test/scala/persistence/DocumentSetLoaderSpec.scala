package persistence

import anorm._
import anorm.SqlParser
import helpers.DbSetup._
import helpers.DbSpecification

class DocumentSetLoaderSpec extends DbSpecification {
  step(setupDb)
  
  "DocumentSetLoader" should {
    
    "load query and title from id" in new DbTestContext {
      val query = "DocumentSetLoaderSpec"
      val title = "DocumentSet for " + query
      
      val documentSetId = insertDocumentSet(query)

      val foundData = DocumentSetLoader.loadQuery(documentSetId)
      foundData must beSome.like { case d =>
	d._1 must be equalTo(title)
	d._2 must be equalTo(query)
      }     
    }
  }
  
  
  step(shutdownDb)
}
