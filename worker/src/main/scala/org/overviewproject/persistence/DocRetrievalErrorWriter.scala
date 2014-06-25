package org.overviewproject.persistence

import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentProcessingError


object DocRetrievalErrorWriter {
  def write(documentSetId: Long, errors: Seq[DocumentRetrievalError]) {
    val documentProcessingErrors = 
      errors.map(e => DocumentProcessingError(documentSetId, e.url, e.message, e.statusCode, e.headers))
    
    Schema.documentProcessingErrors.insert(documentProcessingErrors)

    update(Schema.documentSets)(ds =>
      where(ds.id === documentSetId)
      set (ds.documentProcessingErrorCount := ds.documentProcessingErrorCount.~ + documentProcessingErrors.length)
    )
  }
}
