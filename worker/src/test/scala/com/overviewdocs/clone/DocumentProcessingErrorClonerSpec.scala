package com.overviewdocs.clone

import com.overviewdocs.models.tables.DocumentProcessingErrors
import com.overviewdocs.test.DbSpecification

class DocumentProcessingErrorClonerSpec extends DbSpecification {
  "DocumentProcessingErrorCloner" should {
    
    "clone DocumentProcessingErrors" in new DbScope {
      import database.api._

      val sourceDocumentSet = factory.documentSet()
      factory.documentProcessingError(documentSetId=sourceDocumentSet.id)
      factory.documentProcessingError(documentSetId=sourceDocumentSet.id)

      val cloneDocumentSet = factory.documentSet()

      DocumentProcessingErrorCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)

      blockingDatabase.length(DocumentProcessingErrors.filter(_.documentSetId === cloneDocumentSet.id)) must beEqualTo(2)
    }
  }
}
