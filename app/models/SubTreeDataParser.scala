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
  def createDocuments(documentData: Seq[DocumentData]) : Seq[core.Document] = {
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4))
  }
  
  private def createOneNode(id: Long, 
		  			        descriptions: Map[Long, String],
		  			        childNodeIds: Map[Long, Seq[Long]],
		  			        documentIds: Map[Long, Seq[Long]],
		  			        documentCounts: Map[Long, Long],
		  			        tagCounts: Map[Long, Seq[(Long, Long)]]) : core.Node = {
    
    val documentIdList = core.DocumentIdList(documentIds(id), documentCounts(id))
    core.Node(id, descriptions(id), childNodeIds(id), documentIdList, 
    		  tagCounts.getOrElse(id, Nil))
  }
  
  private def groupByNodeId[A](nodeData: Seq[(Long, A)]) : Map[Long, Seq[A]] = {
    val groupedByNode = nodeData.groupBy(_._1)
    
    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(_._2))
    }
  }
    
  private def mapNodesToChildNodeIdLists(nodeData: Seq[NodeData]) : Map[Long, Seq[Long]] = {
    val nodeAndPossibleChild = nodeData.map(d => (d._1, d._2))
    val possibleChildNodes = groupByNodeId(nodeAndPossibleChild)
    
    possibleChildNodes.map(d => (d._1 -> d._2.flatMap(_.toList)))   
  }
  
  private def mapNodesToDocumentIdLists(documentData: Seq[NodeDocument]) : Map[Long, Seq[Long]] = {
    val nodeAndDocument = documentData.map(d => (d._1, d._3))
    groupByNodeId(nodeAndDocument)
  }
  
  private def mapNodesToDescriptions(nodeData: Seq[NodeData]) : Map[Long, String] = {
    val childNodes = nodeData.filter(_._2 != None)
    childNodes.map(d => (d._2.get, d._3)).distinct.toMap
  }
  
  private def mapNodesToDocumentCounts(documentData: Seq[NodeDocument]) : Map[Long, Long] = {
    documentData.map(d => (d._1, d._2)).distinct.toMap
  }
  
  private def mapNodesToTagCounts(nodeTagCountData: Seq[NodeTagCountData]) :
	  Map[Long, Seq[(Long, Long)]] = {
    val groupedByNode = nodeTagCountData.groupBy(_._1)
    
    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(d => (d._2, d._3)))
    }
  }
  
  private def realNodeIds(nodeData : Seq[NodeData]) : Seq[Long] = {
    nodeData.map(_._1).distinct.filterNot(_ == NoId)
  }
}