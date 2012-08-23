package persistence

import anorm._
import anorm.SqlParser
import helpers.DbSetup._
import helpers.DbSpecification

class DocumentSetLoaderSpec extends DbSpecification {
  step(setupDb)
  
  "DocumentSetLoader" should {
    
    "load query from id" in new DbTestContext {
      val query = "DocumentSetLoaderSpec"
      val documentSetId = insertDocumentSet(query)

      val foundQuery = DocumentSetLoader.loadQuery(documentSetId)
      foundQuery must beSome     
      foundQuery.get must be equalTo(query)
    }
  }
  
  
  step(shutdownDb)
}