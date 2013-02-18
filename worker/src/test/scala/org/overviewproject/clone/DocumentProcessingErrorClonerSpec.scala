package org.overviewproject.clone

import org.overviewproject.persistence.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification

class DocumentProcessingErrorClonerSpec extends DbSpecification {
  
  step(setupDb)
  
  "DocumentProcessingErrorCloner" should {
    
    "clone DocumentProcessingErrors" in new DbTestContext {
      val sourceDocumentSetId = insertDocumentSet("DocumentProcessingErrorSpec")
      val cloneDocumentSetId = insertDocumentSet("CloneDocumentProcessingErrorSpec")
      
      DocumentProcessingErrorCloner.clone(sourceDocumentSetId, cloneDocumentSetId)
      
      val clonedErrors = Schema.documentProcessingErrors.where(dpe => dpe.documentSetId === cloneDocumentSetId)
    }
  }
  
  step(shutdownDb)
}