package org.overviewproject.persistence

import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentSet


class DocRetrievalErrorWriterSpec extends DbSpecification {
  step(setupDb)

  trait OurContext extends DbTestContext {
    var documentSetId: Long = _
    var errors: Seq[DocumentRetrievalError] = _
    
    override def setupWithDb = {
      documentSetId = Schema.documentSets.insert(DocumentSet())
      errors = Seq.tabulate(10)(i => DocumentRetrievalError("url" + i, "error: " + i, Some(i), Some("header")))
    }
  }
  
  inExample("write out error data") in new OurContext {
    DocRetrievalErrorWriter.write(documentSetId, errors)    
    val foundErrors = Schema.documentProcessingErrors.where(dpe => dpe.documentSetId === documentSetId)
    
    foundErrors.size must be equalTo 10
    foundErrors.head.headers must beSome("header")
  }

  inExample("write document_set.document_processing_error_count") in new OurContext {
    DocRetrievalErrorWriter.write(documentSetId, errors)
    val documentSet = Schema.documentSets.get(documentSetId)

    documentSet.documentProcessingErrorCount must beEqualTo(errors.length)
  }
  
  step(shutdownDb)
}
