/*
 * DatabaseStructure.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models

/**
 * Result types from different database queries.
 */
object DatabaseStructure {
  /**
   * parentId, Some(childId), description. Description is the description of childId.
   *  If parentId has no child, None appears in the childId place, and description is ""
   */
  type NodeData = (Long, Option[Long], String)

  /**
   * nodeId, documentCount, documentId. DocumentCount is the total number
   *  of documents contained by the Node.
   */
  type NodeDocument = (Long, Long, Long)

  /** documentId, nodeId */
  type DocumentNodeData = (Long, Long)

  /**  nodeId, tagId, count */
  type NodeTagCountData = (Long, Long, Long)

  /** documentCount, documentId */
  type DocumentListData = (Long, Option[Long])

  /** used as parentId if a node has no parent */
  val NoId = -1l;
}
