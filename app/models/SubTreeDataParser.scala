/*
 * SubTreeDataParser.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models

import DatabaseStructure._
import org.overviewproject.tree.orm.Node

/**
 * Utility class for SubTreeLoader that parses the results from the database queries
 */
class SubTreeDataParser extends DocumentListParser {

  def addTagDataToNodes(nodes: Seq[core.Node], nodeTagCountData: Seq[NodeTagCountData]): Seq[core.Node] = {
    val tagCounts = mapNodesToTagCounts(nodeTagCountData)

    nodes.map(n => n.copy(tagCounts = tagCounts.getOrElse(n.id, Map())))
  }
  
  private def mapNodesToTagCounts(nodeTagCountData: Seq[NodeTagCountData]): Map[Long, Map[String, Long]] = {
    val groupedByNode = nodeTagCountData.groupBy(_._1)

    groupedByNode.map {
      case (nodeId, dataList) => (nodeId, dataList.map(d => (d._2.toString -> d._3)).toMap)
    }
  }
}
