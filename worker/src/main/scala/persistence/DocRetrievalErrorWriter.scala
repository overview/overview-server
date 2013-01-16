package persistence

import org.overviewproject.http.DocRetrievalError
import org.overviewproject.tree.orm.DocumentProcessingError


object DocRetrievalErrorWriter {
  def write(documentSetId: Long, errors: Seq[DocRetrievalError]) {
    val documentProcessingErrors = 
      errors.map(e => DocumentProcessingError(documentSetId, e.documentUrl, e.message, e.statusCode))
    
    Schema.documentProcessingErrors.insert(documentProcessingErrors)
  }
}
