package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class PersistentDocumentListDataSaver extends PersistentDocumentListSelector {

  def addTag(tagId: Long, nodeIds: Seq[Long], documentIds: Seq[Long])
            (implicit c: Connection): Long = {
    val selectedDocumentsWhere = createWhereClause(nodeIds, documentIds)
    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document
        WHERE id IN 
          (SELECT id FROM document """ + 
           selectedDocumentsWhere +
        """
          )
        """).on("tagId" -> tagId).executeUpdate()
  }
}