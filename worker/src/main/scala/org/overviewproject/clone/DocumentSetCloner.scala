package org.overviewproject.clone

trait DocumentSetCloner {
  
  type DocumentIdMap = Map[Long, Long]
  type NodeIdMap = Map[Long, Long]
  type TagIdMap = Map[Long, Long]
  
  val cloneDocuments: (Long, Long) => DocumentIdMap
  val cloneNodes: (Long, Long, DocumentIdMap) => NodeIdMap
  val cloneTags: (Long, Long) => TagIdMap
  
  val cloneNodeDocuments: (DocumentIdMap, NodeIdMap) => Unit
  val cloneDocumentTags: (DocumentIdMap, TagIdMap) => Unit  
    
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {
    val documentIdMapping = cloneDocuments(sourceDocumentSetId, cloneDocumentSetId)
    val nodeIdMapping = cloneNodes(sourceDocumentSetId, cloneDocumentSetId, documentIdMapping)
    val tagIdMapping = cloneTags(sourceDocumentSetId, cloneDocumentSetId)
    
    cloneNodeDocuments(documentIdMapping, nodeIdMapping)
    cloneDocumentTags(documentIdMapping, tagIdMapping)
  }

}

object CloneDocumentSet {
  
  def apply(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {
    val cloner = new DocumentSetCloner {
      override val cloneDocuments = DocumentCloner.clone _
      override val cloneNodes = NodeCloner.clone _
      override val cloneTags = TagCloner.clone _
      
      override val cloneNodeDocuments = NodeDocumentCloner.clone _
      override val cloneDocumentTags = DocumentTagCloner.clone _
    }
    
    cloner.clone(sourceDocumentSetId, cloneDocumentSetId)
  }
}