package org.overviewproject.clone

class DocumentCloner {

}

object DocumentCloner {
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {
    import persistence.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._
    
    val sourceDocuments = Schema.documents.where(d => d.documentSetId === sourceDocumentSetId)
    val clones = sourceDocuments.map(_.copy(id = 0, documentSetId = cloneDocumentSetId))
    Schema.documents.insert(clones)
  }
}