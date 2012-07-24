package models

import anorm.SqlParser._

object DatabaseStructure {
  type NodeData = (Long, Long, String) // parentId, childId, description
  type NodeDocument = (Long, Long, Long) // nodeId, documentCount, documentId
  type DocumentData = (Long, String, String, String) // documentId, title, textUrl, viewUrl
  
  val IdColumn = "id"
  val ChildIdColumn = "child_id"
  val DescriptionColumn = "child_description"
  val DocumentCountColumn = "document_count"
  val DocumentIdColumn = "document_id"
  val TitleColumn = "title"
  val TextUrlColumn = "text_url"
  val ViewUrlColumn = "view_url"
  
  val DocumentIdParser = long(IdColumn) ~ long(DocumentCountColumn) ~ long(DocumentIdColumn)
  val DocumentParser = long(IdColumn) ~ str(TitleColumn) ~ str(TextUrlColumn) ~ str(ViewUrlColumn)
  val NodeParser = long(IdColumn) ~ long(ChildIdColumn) ~ str(DescriptionColumn)
  

}