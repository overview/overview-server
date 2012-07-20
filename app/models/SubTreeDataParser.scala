package models

class SubTreeDataParser {

  def createNodes(data: List[(Long, Long, String)]) : List[core.Node] = {
    data.map(d => core.Node(d._2, d._3))
  }
}