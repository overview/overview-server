package org.overviewproject.persistence

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.http.DocRetrievalError
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.tree.orm.DocumentProcessingError
import org.overviewproject.documentcloud.DocumentRetrievalError


object DocRetrievalErrorWriter {
  def write(documentSetId: Long, errors: Seq[DocumentRetrievalError]) {
    val documentProcessingErrors = 
      errors.map(e => DocumentProcessingError(documentSetId, e.url, e.message, e.statusCode, e.headers))
    
    Schema.documentProcessingErrors.insert(documentProcessingErrors)

    update(Schema.documentSets)(ds =>
      where(ds.id === documentSetId)
      set (ds.documentProcessingErrorCount := documentProcessingErrors.length)
    )
  }
}
