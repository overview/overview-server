package org.overviewproject.clone

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.models.tables.DocumentProcessingErrors
import org.overviewproject.test.DbSpecification

class DocumentProcessingErrorClonerSpec extends DbSpecification {
  "DocumentProcessingErrorCloner" should {
    
    "clone DocumentProcessingErrors" in new DbScope {
      import database.api._

      val sourceDocumentSet = factory.documentSet()
      factory.documentProcessingError(documentSetId=sourceDocumentSet.id)
      factory.documentProcessingError(documentSetId=sourceDocumentSet.id)

      val cloneDocumentSet = factory.documentSet()

      DeprecatedDatabase.inTransaction {
        DocumentProcessingErrorCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)
      }

      blockingDatabase.length(DocumentProcessingErrors.filter(_.documentSetId === cloneDocumentSet.id)) must beEqualTo(2)
    }
  }
}
