package models

import DatabaseStructure.{NodeData, NodeDocument}

/**
 * Utility class for SubTreeLoader that parses the results from the database queries
 */
class SubTreeDataParser {

  /**
   * @return a list of Nodes created from the passed in data
   */
  def createNodes(nodeData: List[NodeData], 
		  		  documentData: List[NodeDocument]) : List[core.Node] = {
    val nodeAndChild = nodeData.map(d => (d._1, d._2))
    val childNodeIds = groupByNodeId(nodeAndChild)
    
    val nodeAndDocument = documentData.map(d => (d._1, d._3))
    val documentIds = groupByNodeId(nodeAndDocument)
    
    val nodeAndDocumentCount = documentData.map(d => (d._1, d._2))
    val documentCounts = groupByNodeId(nodeAndDocumentCount)
    
    nodeData.map(d => createOneNode(d._2, d._3, childNodeIds, documentIds, documentCounts))
  }
  
  private def createOneNode(id: Long, 
		  			        description: String,
		  			        childNodeIds: Map[Long, List[Long]],
		  			        documentIds: Map[Long, List[Long]],
		  			        documentCounts: Map[Long, List[Long]]) : core.Node = {
    val documentIdLists = documentIds.map {
      case (node, ids) => (node, core.DocumentIdList(ids, documentCounts.getOrElse(node, Nil).head))
    }
    
    core.Node(id, description, childNodeIds.getOrElse(id, Nil),
    						   documentIdLists.getOrElse(id, null))
    						   
  }
  
  private def groupByNodeId(nodeData: List[(Long, Long)]) : Map[Long, List[Long]] = {
    val groupedByNode = nodeData.groupBy(_._1)
    
    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(_._2))
    }
  }
    
}