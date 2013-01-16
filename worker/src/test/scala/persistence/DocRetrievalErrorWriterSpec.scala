package persistence

import persistence.Schema.documentProcessingErrors
import org.overviewproject.http.DocRetrievalError
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentProcessingError


class DocRetrievalErrorWriterSpec extends DbSpecification {

  step(setupDb)
  
  "write out error data" in new DbTestContext {
    val documentSetId = insertDocumentSet("DocRetrievalErrorWriterSpec")
    
    val errors = Seq.tabulate(10)(i => DocRetrievalError("url" + i, "error: " + i))
        
    DocRetrievalErrorWriter.write(documentSetId, errors)    
    val foundErrors = documentProcessingErrors.where(dpe => dpe.documentSetId === documentSetId)
    
    foundErrors.size must be equalTo 10
  }
  
  step(shutdownDb)
}