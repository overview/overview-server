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

  /** documentId, title, documentCloudId */
  type DocumentData = (Long, String, String)

  /**  nodeId, tagId, count */
  type NodeTagCountData = (Long, Long, Long)

  /** documentId, tagId */
  type DocumentTagData = (Long, Long)

  /**
   * tagId, name, count, Some(documentId).
   * If there are no documents with the tag, None appears in the documentId place
   */
  type TagData = (Long, String, Long, Option[Long])

  /** used as parentId if a node has no parent */
  val NoId = -1l;
}
