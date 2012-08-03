package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class PersistentDocumentListDataSaver extends PersistentDocumentListSelector {

  def addTag(tagId: Long, nodeIds: Seq[Long], documentIds: Seq[Long])
            (implicit c: Connection): Long = {
    val whereClauses = SelectionWhere(nodeIds, documentIds) :+ whereDocumentNotTagged
    val whereSelectionIsNotAlreadyTagged = combineWhereClauses(whereClauses)
    
    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document """ +
          whereSelectionIsNotAlreadyTagged).on("tagId" -> tagId).executeUpdate()
  }
  
  def removeTag(tagId: Long, nodeIds: Seq[Long], documentIds: Seq[Long])
               (implicit c: Connection): Long = {
    val whereClauses = SelectionWhere(nodeIds, documentIds)
    val whereSelected = combineWhereClauses(whereClauses)
    
    SQL("""
        DELETE FROM document_tag WHERE document_id IN
    	  (SELECT id FROM document """ + 
    	   whereSelected + ")").executeUpdate()
  }
  
  
  private val whereDocumentNotTagged = 
    Some("id NOT IN (SELECT document_id FROM document_tag WHERE tag_id = {tagId})")

}