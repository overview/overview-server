package org.overviewproject.clone


object DocumentCloner {
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Map[Long, Long] = {
    import persistence.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._
    
    val sourceDocuments = Schema.documents.where(d => d.documentSetId === sourceDocumentSetId)
    val cloneIds = sourceDocuments.map { d =>
      val clone = d.copy(id = 0, documentSetId = cloneDocumentSetId)
      Schema.documents.insert(clone)
      clone.id
    }

    sourceDocuments.map(_.id).zip(cloneIds).toMap
  }
}