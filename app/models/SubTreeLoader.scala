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

  /**
   * @return a list of all the Nodes in the subTree with root at nodeId
   */
  def load(nodeId: Long, depth: Int)(implicit connection: Connection): Seq[core.Node] = {

    val allNodeData = loader.loadNodeData(documentSetId, nodeId, depth + 1)

    val treeNodeData = nodesUntilDepth(nodeId, allNodeData, depth)

    val allNodeIds = allNodeData.map(_._1).distinct
    val treeNodeIds = treeNodeData.map(_._1).distinct

    val documentData = loader.loadDocumentIds(allNodeIds)
    val nodeTagCountData = loader.loadNodeTagCounts(treeNodeIds)

    parser.createNodes(treeNodeData, documentData, nodeTagCountData)
  }

  def load(node: core.Node, depth: Int)(implicit connection: Connection): Seq[core.Node] = {
    val nodes = nodeLoader.loadTree(documentSetId, node, depth + 1)
    val root = nodes.find(_.id == node.id).get // make sure child ids are loaded
    
    val treeNodes = treeUntilDepth(root, nodes, depth)
    
    val nodeIds = treeNodes.map(_.id)

    val nodeTagCountData = loader.loadNodeTagCounts(nodeIds)

    parser.addTagDataToNodes(treeNodes, nodeTagCountData)
  }

  /**
   * @return Some(rootId) if the documentSet has a root node, None otherwise.
   */
  def loadRootId()(implicit connection: Connection): Option[Long] = {
    loader.loadRoot(documentSetId)
  }

  def loadRoot()(implicit connection: Connection): Option[Node] = {
    nodeLoader.loadRoot(documentSetId)
  }
  
  def loadNode(id: Long)(implicit connection: Connection): Option[Node] = {
    nodeLoader.loadNode(documentSetId, id)
  }
  /**
   * @return a list of Documents whose ids are referenced by the passed in nodes and tags.
   * The list is sorted by document IDs and all the elements are distinct, even if documentIds
   * referenced multiple times.
   */
  def loadDocuments(nodes: Seq[core.Node], tags: Seq[PersistentTagInfo])(implicit connection: Connection): Seq[core.Document] = {
    val nodeDocumentIds = nodes.flatMap(_.documentIds.firstIds)
    val tagDocumentIds = tags.flatMap(_.documentIds.firstIds)
    val documentIds = nodeDocumentIds ++ tagDocumentIds

    loadDocumentList(documentIds.distinct.sorted)
  }

  def loadTags(documentSetId: Long)(implicit connection: Connection): Seq[PersistentTagInfo] = {
    val tagData = loader.loadTags(documentSetId)
    parser.createTags(tagData)
  }

  private def treeUntilDepth(root: core.Node, nodes: Seq[core.Node], depth: Int): Seq[Node] = {
    if (depth == 0) Seq(root)
    else {
      val childNodes = root.childNodeIds.flatMap(c => nodes.find(_.id == c))
      root +: childNodes.flatMap(treeUntilDepth(_, nodes, depth - 1))
    }
  }

  private def nodesUntilDepth(rootId: Long, nodeData: List[NodeData], depth: Int): List[NodeData] = {
    val root = nodeData.find(_._2 == Some(rootId)).get
    val children: List[NodeData] = nodeData.filter(_._1 == rootId)

    if (depth == 1) root :: children
    else {
      root :: children.flatMap {
        case (_, Some(c), _) => nodesUntilDepth(c, nodeData, depth - 1)
        case leaf => List(leaf)
      }
    }

  }
}
