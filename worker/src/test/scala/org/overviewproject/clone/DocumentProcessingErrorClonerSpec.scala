package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentSet

class DocumentProcessingErrorClonerSpec extends DbSpecification {
  
  step(setupDb)
  
  "DocumentProcessingErrorCloner" should {
    
    "clone DocumentProcessingErrors" in new DbTestContext {
      val sourceDocumentSet = Schema.documentSets.insert(DocumentSet(title = "DocumentProcessingErrorSpec"))
      val cloneDocumentSet = Schema.documentSets.insert(DocumentSet(title = "CloneDocumentProcessingErrorSpec"))
      
      DocumentProcessingErrorCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)
      
      val clonedErrors = Schema.documentProcessingErrors.where(dpe => dpe.documentSetId === cloneDocumentSet.id)
    }
  }
  
  step(shutdownDb)
}