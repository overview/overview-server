package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class PersistentDocumentListDataSaver extends PersistentDocumentListSelector {

  def addTag(tagId: Long, nodeIds: Seq[Long], documentIds: Seq[Long])
            (implicit c: Connection): Long = {
    val selectedDocumentsWhere = createWhereClause(nodeIds, documentIds)
    
    val w = if (selectedDocumentsWhere == "") "WHERE" else selectedDocumentsWhere + " AND "

    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document """ +
            w +
        """
        id NOT IN
          (SELECT document_id FROM document_tag WHERE tag_id = {tagId})
        """).on("tagId" -> tagId).executeUpdate()
  }
}