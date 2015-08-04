package com.overviewdocs.clone

import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.persistence.orm.Schema

object DocumentProcessingErrorCloner {

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {
    val sourceErrors = Schema.documentProcessingErrors.where(dpe => dpe.documentSetId === sourceDocumentSetId)
    
    val cloneErrors = sourceErrors.map(_.copy(documentSetId = cloneDocumentSetId))

    Schema.documentProcessingErrors.insert(cloneErrors)
  }
}