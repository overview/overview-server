package org.overviewproject.clone

import org.overviewproject.persistence.DocumentSetIdGenerator


object DocumentCloner {
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Map[Long, Long] = {
    import org.overviewproject.persistence.orm.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._
    val ids = new DocumentSetIdGenerator(cloneDocumentSetId)
    
    val sourceDocuments = Schema.documents.where(d => d.documentSetId === sourceDocumentSetId)
    val cloneIds = sourceDocuments.map { d =>
      val clone = d.copy(id = ids.next, documentSetId = cloneDocumentSetId)
      Schema.documents.insert(clone)
      clone.id
    }

    sourceDocuments.map(_.id).zip(cloneIds).toMap
  }
}