package models


/**
 * Utility class for SubTreeLoader that parses the results from the database queries
 */
class SubTreeDataParser {

  /**
   * @return a list of Nodes created from the passed in data
   */
  def createNodes(nodeData: List[(Long, Long, String)], 
		  		  documentData: List[(Long, Long, Long)]) : List[core.Node] = {
    val nodeAndChild = nodeData.map(d => (d._1, d._2))
    val childNodeIds = groupByNodeId(nodeAndChild)
    
    val nodeAndDocument = documentData.map(d => (d._1, d._3))
    val documentIds = groupByNodeId(nodeAndDocument)
    
    nodeData.map(d => createOneNode(d._2, d._3, childNodeIds, documentIds))
  }
  
  private def createOneNode(id: Long, 
		  			        description: String,
		  			        childNodeIds: Map[Long, List[Long]],
		  			        documentIds: Map[Long, List[Long]]) : core.Node = {
    core.Node(id, description, childNodeIds.getOrElse(id, Nil), 
    						   documentIds.getOrElse(id, Nil))
  }
  
  private def groupByNodeId(nodeData: List[(Long, Long)]) : Map[Long, List[Long]] = {
    val groupedByNode = nodeData.groupBy(_._1)
    
    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(_._2))
    }
  }
    
}