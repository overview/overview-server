package models

import anorm.SqlParser._

object DatabaseStructure {
  type NodeData = (Long, Option[Long], String) // parentId, childId, description
  type NodeDocument = (Long, Long, Long) // nodeId, documentCount, documentId
  type DocumentData = (Long, String, String, String) // documentId, title, textUrl, viewUrl
  type NodeTagCountData = (Long, Long, Long) // nodeId, tagId, count
  type DocumentTagData = (Long, Long) // documentId, tagId
  
  val NoId = -1l;
}