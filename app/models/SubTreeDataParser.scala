package models

class SubTreeDataParser {

  def createNodes(data: List[(Long, Long, String)]) : List[core.Node] = {
    val parentData = data.groupBy(d => d._1)
    val childNodeIds = parentData.map { 
      case (node, dataList) => (node, dataList.map(_._2))
    }

    
    data.map(d => core.Node(d._2, d._3, childNodeIds.getOrElse(d._2, Nil)))
  }
}