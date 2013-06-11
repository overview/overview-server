/*
 * SubTreeLoader.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models

import anorm._
import anorm.SqlParser._
import DatabaseStructure.NodeData
import java.sql.Connection
import play.api.Play.current
import play.api.db.DB
import models.core.Node

/**
 * Loads data from the database about subTrees
 */
class SubTreeLoader(documentSetId: Long, loader: SubTreeDataLoader = new SubTreeDataLoader(),
  nodeLoader: NodeLoader = new NodeLoader(),
  parser: SubTreeDataParser = new SubTreeDataParser()) {

  def load(nodeId: Long, depth: Int)(implicit connection: Connection): Seq[core.Node] = {
    val nodes = nodeLoader.loadTree(documentSetId, nodeId, depth)
    val nodeIds = nodes.map(_.id)

    val nodeTagCountData = loader.loadNodeTagCounts(nodeIds)

    parser.addTagDataToNodes(nodes, nodeTagCountData)
  }

  /**
   * @return Some(rootId) if the documentSet has a root node, None otherwise.
   */
  def loadRootId()(implicit connection: Connection): Option[Long] = {
    nodeLoader.loadRootId(documentSetId)
  }
}
