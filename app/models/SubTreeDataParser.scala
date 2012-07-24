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
    val parentData = nodeData.groupBy(d => d._1)
    val childNodeIds = parentData.map { 
      case (node, dataList) => (node, dataList.map(_._2))
    }
    
    val parentDocumentData = documentData.groupBy(_._1)
    val documentIds = parentDocumentData.map {
      case (node, documentDataList) => (node, documentDataList.map(_._3))
    }
    nodeData.map(d => createOneNode(d._2, d._3, childNodeIds, documentIds))
  }
  
  private def createOneNode(id: Long, 
		  			        description: String,
		  			        childNodeIds: Map[Long, List[Long]],
		  			        documentIds: Map[Long, List[Long]]) : core.Node = {
    core.Node(id, description, childNodeIds.getOrElse(id, Nil), documentIds.getOrElse(id, Nil))
  }
}