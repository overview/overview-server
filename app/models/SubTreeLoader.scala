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
  parser: SubTreeDataParser = new SubTreeDataParser()) extends DocumentListLoader(loader, parser) {

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
  
  /**
   * @return a list of Documents whose ids are referenced by the passed in nodes and tags.
   * The list is sorted by document IDs and all the elements are distinct, even if documentIds
   * referenced multiple times.
   */
  def loadDocuments(nodes: Seq[core.Node])(implicit connection: Connection): Seq[core.Document] = {
    val nodeDocumentIds = nodes.flatMap(_.documentIds.firstIds)
    val documentIds = nodeDocumentIds

    loadDocumentList(documentIds.distinct.sorted)
  }

  def loadTags(documentSetId: Long)(implicit connection: Connection): Seq[PersistentTagInfo] = {
    val tagData = loader.loadTags(documentSetId)
    parser.createTags(tagData)
  }
}
