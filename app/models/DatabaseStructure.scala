package models

import anorm.SqlParser._

object DatabaseStructure {
  type NodeData = (Long, Option[Long], String) // parentId, childId, description
  type NodeDocument = (Long, Long, Long) // nodeId, documentCount, documentId
  type DocumentData = (Long, String, String, String) // documentId, title, textUrl, viewUrl
  
  val NoId = -1l;
}