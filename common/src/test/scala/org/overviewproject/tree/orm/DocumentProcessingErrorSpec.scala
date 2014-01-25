package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification

class DocumentProcessingErrorSpec extends DbSpecification {
  step(setupDb)
  
  "DocumentProcessingError" should {

    "write and read from the database" in new DbTestContext {
      val documentSet = Schema.documentSets.insert(DocumentSet(title = "DocumentProcessingErrorSpec"))
      
      val documentProcessingError = 
        DocumentProcessingError(documentSet.id, "url", "message", Some(404), Some("header"))
        
      Schema.documentProcessingErrors.insert(documentProcessingError)
      
      documentProcessingError.id must not be equalTo(0)
      
      val foundError = Schema.documentProcessingErrors.lookup(documentProcessingError.id)
      
      foundError must beSome
      foundError.get must be equalTo(documentProcessingError)
    }
  } 
  
  step(shutdownDb)

}