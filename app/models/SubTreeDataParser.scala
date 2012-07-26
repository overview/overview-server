package models

import DatabaseStructure.{DocumentData, NodeData, NodeDocument, NoId}

/**
 * Utility class for SubTreeLoader that parses the results from the database queries
 */
class SubTreeDataParser {

  /**
   * @return a list of Nodes created from the passed in data
   */
  def createNodes(nodeData: List[NodeData], 
		  		  documentData: List[NodeDocument]) : List[core.Node] = {
    val nodeAndPossibleChild = nodeData.map(d => (d._1, d._2))
    val possibleChildNodes = groupByNodeId(nodeAndPossibleChild)
    val childNodeIds = possibleChildNodes.map(d => (d._1 -> d._2.flatMap(_.toList)))
    
    val nodeAndDocument = documentData.map(d => (d._1, d._3))
    val documentIds = groupByNodeId(nodeAndDocument)
    
    val nodeDescriptions = nodeData.filter(_._2 != None).map(d => (d._2.get, d._3)).distinct.toMap
    
    val nodeIds = nodeData.map(_._1).distinct.filterNot(_ == NoId)
    val documentCounts = documentData.map(d => (d._1, d._2)).distinct.toMap
    
    nodeIds.map(n => createOneNode(n, nodeDescriptions,
                                      childNodeIds,
                                      documentIds,
                                      documentCounts))	
  }
  
  /**
   * @return a list of Documents created from the passed in data
   */
  def createDocuments(documentData: List[DocumentData]) : List[core.Document] = {
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4))
  }
  
  private def createOneNode(id: Long, 
		  			        descriptions: Map[Long, String],
		  			        childNodeIds: Map[Long, List[Long]],
		  			        documentIds: Map[Long, List[Long]],
		  			        documentCounts: Map[Long, Long]) : core.Node = {
    
    val documentIdList = core.DocumentIdList(documentIds(id), documentCounts(id))
    core.Node(id, descriptions(id), childNodeIds(id), documentIdList)
    						   
  }
  
  private def groupByNodeId[A](nodeData: List[(Long, A)]) : Map[Long, List[A]] = {
    val groupedByNode = nodeData.groupBy(_._1)
    
    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(_._2))
    }
  }
    
}