package org.overviewproject.persistence

import org.overviewproject.http.DocRetrievalError
import org.overviewproject.persistence.Schema.documentProcessingErrors;
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentProcessingError


class DocRetrievalErrorWriterSpec extends DbSpecification {

  step(setupDb)
  
  inExample("write out error data") in new DbTestContext {
    val documentSetId = insertDocumentSet("DocRetrievalErrorWriterSpec")
    
    val errors = Seq.tabulate(10)(i => DocRetrievalError("url" + i, "error: " + i, Some(i), Some("header")))
        
    DocRetrievalErrorWriter.write(documentSetId, errors)    
    val foundErrors = documentProcessingErrors.where(dpe => dpe.documentSetId === documentSetId)
    
    foundErrors.size must be equalTo 10
    foundErrors.head.headers must beSome.like { case h => h must be equalTo("header") }
  }
  
  step(shutdownDb)
}