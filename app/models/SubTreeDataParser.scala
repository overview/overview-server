package models

class SubTreeDataParser {

  def createNodes(data: List[(Long, Long, String)]) : List[core.Node] = {
    val parentData = data.groupBy(d => d._1)
    val childNodeIds = parentData.map { 
      case (node, dataList) => (node, dataList.map(_._2))
    }
    
    data.map(d => createOneNode(d._2, d._3, childNodeIds))
  }
  
  private def createOneNode(id: Long, description: String, childNodeIds: Map[Long, List[Long]]) : core.Node = {
    core.Node(id, description, childNodeIds.getOrElse(id, Nil))
  }
}