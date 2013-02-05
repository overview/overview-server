package org.overviewproject.clone

import org.overviewproject.postgres.SquerylEntrypoint._
import persistence.Schema

object DocumentProcessingErrorCloner {

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {
    val sourceErrors = Schema.documentProcessingErrors.where(dpe => dpe.documentSetId === sourceDocumentSetId)
    
    val cloneErrors = sourceErrors.map(_.copy(documentSetId = cloneDocumentSetId))

    Schema.documentProcessingErrors.insert(cloneErrors)
  }
}