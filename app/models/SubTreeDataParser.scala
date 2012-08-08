package models

import DatabaseStructure._

/**
 * Utility class for SubTreeLoader that parses the results from the database queries
 */
class SubTreeDataParser {

  /**
   * @return a list of Nodes created from the passed in data
   */
  def createNodes(nodeData: Seq[NodeData], 
		  		  documentData: Seq[NodeDocument],
		  		  nodeTagCountData: Seq[NodeTagCountData]) : Seq[core.Node] = {
    val nodeDescriptions = mapNodesToDescriptions(nodeData)
    val childNodeIds = mapNodesToChildNodeIdLists(nodeData)
    val documentIds = mapNodesToDocumentIdLists(documentData)
    val documentCounts = mapNodesToDocumentCounts(documentData)
    val tagCounts = mapNodesToTagCounts(nodeTagCountData)
    
    val nodeIds = realNodeIds(nodeData) 

    nodeIds.map(n => createOneNode(n, nodeDescriptions,
                                      childNodeIds,
                                      documentIds,
                                      documentCounts,
                                      tagCounts))	
  }
  
  /**
   * @return a list of Documents created from the passed in data
   */
  def createDocuments(documentData: Seq[DocumentData], 
		  			  documentTagData: Seq[DocumentTagData]) : Seq[core.Document] = {
    val tagIds = mapDocumentsToTagIds(documentTagData)
    
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4, tagIds.getOrElse(d._1, Nil)))
  }
  
  def createTags(tagData: Seq[TagData]) : Seq[core.Tag] = {
    val tagNames = mapTagsToNames(tagData)
    val tagDocumentCounts = mapTagsToDocumentCounts(tagData)
    val tagDocuments = mapTagsToDocuments(tagData)
    
    val tagIds = tagNames.keys.toSeq
    tagIds.map(id => core.Tag(id, tagNames(id), 
    				          core.DocumentIdList(tagDocuments(id), tagDocumentCounts(id))))

  }
  
  private def createOneNode(id: Long, 
		  			        descriptions: Map[Long, String],
		  			        childNodeIds: Map[Long, Seq[Long]],
		  			        documentIds: Map[Long, Seq[Long]],
		  			        documentCounts: Map[Long, Long],
		  			        tagCounts: Map[Long, Map[String, Long]]) : core.Node = {
    
    val documentIdList = core.DocumentIdList(documentIds(id), documentCounts(id))
    core.Node(id, descriptions(id), childNodeIds(id), documentIdList, 
    		  tagCounts.getOrElse(id, Map()))
  }
  
  private def groupById[A](data: Seq[(Long, A)]) : Map[Long, Seq[A]] = {
    val groupedById = data.groupBy(_._1)
    
    groupedById.map {
      case (id, dataList) => (id, dataList.map(_._2))
    }
  }
    
  private def mapNodesToChildNodeIdLists(nodeData: Seq[NodeData]) : Map[Long, Seq[Long]] = {
    val nodeAndPossibleChild = nodeData.map(d => (d._1, d._2))
    val possibleChildNodes = groupById(nodeAndPossibleChild)
    
    possibleChildNodes.map(d => (d._1 -> d._2.flatMap(_.toList)))   
  }
  
  private def mapNodesToDocumentIdLists(documentData: Seq[NodeDocument]) : Map[Long, Seq[Long]] = {
    val nodeAndDocument = documentData.map(d => (d._1, d._3))
    groupById(nodeAndDocument)
  }
  
  private def mapNodesToDescriptions(nodeData: Seq[NodeData]) : Map[Long, String] = {
    val childNodes = nodeData.filter(_._2 != None)
    childNodes.map(d => (d._2.get, d._3)).distinct.toMap
  }
  
  private def mapNodesToDocumentCounts(documentData: Seq[NodeDocument]) : Map[Long, Long] = {
    documentData.map(d => (d._1, d._2)).distinct.toMap
  }
  
  private def mapNodesToTagCounts(nodeTagCountData: Seq[NodeTagCountData]) :
	  Map[Long, Map[String, Long]] = {
    val groupedByNode = nodeTagCountData.groupBy(_._1)
    
    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(d => (d._2.toString -> d._3)).toMap)
    }
  }
  
  private def mapDocumentsToTagIds(documentTagData: Seq[DocumentTagData]) :
	  Map[Long, Seq[Long]] = {
    groupById(documentTagData)
  }
  
  private def mapTagsToNames(tagData: Seq[TagData]) : Map[Long, String] = {
    tagData.map(d => (d._1, d._2)).distinct.toMap
  }
  
  private def mapTagsToDocumentCounts(tagData: Seq[TagData]) : Map[Long, Long] = {
    tagData.map(d => (d._1, d._3)).distinct.toMap
  }
  
  private def mapTagsToDocuments(tagData: Seq[TagData]) : Map[Long, Seq[Long]] = {
    val tagAndDocument = tagData.map(d => (d._1, d._4))
    val tagsToPossibleDocuments = groupById(tagAndDocument)
    
    tagsToPossibleDocuments.map {
      case (tag, documents) => (tag -> documents.collect { case Some(id) => id}) 
    }
  }
  
  private def realNodeIds(nodeData : Seq[NodeData]) : Seq[Long] = {
    nodeData.map(_._1).distinct.filterNot(_ == NoId)
  }
}