package org.overviewproject.clone

class DocumentCloner(sourceDocumentIds: Iterable[Long], cloneDocumentIds: Iterable[Long]) {
  
  val mapping: Map[Long, Long] = sourceDocumentIds.zip(cloneDocumentIds).toMap

  def getCloneId(sourceDocumentId: Long): Option[Long] = {
    mapping.get(sourceDocumentId)
  }
  
  
}

object DocumentCloner {
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): DocumentCloner = {
    import persistence.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._
    
    val sourceDocuments = Schema.documents.where(d => d.documentSetId === sourceDocumentSetId)
    val cloneIds = sourceDocuments.map { d =>
      val clone = d.copy(id = 0, documentSetId = cloneDocumentSetId)
      Schema.documents.insert(clone)
      clone.id
    }

    new DocumentCloner(sourceDocuments.map(_.id), cloneIds)
  }
}