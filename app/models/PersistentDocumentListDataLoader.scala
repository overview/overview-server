package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader(nodeIds: List[Long], documentIds: List[Long]) {
  
  def loadDocumentSlice(firstRow: Long, maxRows: Long)
                       (implicit c: Connection) : List[DocumentData] = {
    if (nodeIds.isEmpty) {
	  val documentSelectionWhere =
	    "WHERE document.id IN " + idList(documentIds)
	        
	  documentQueryWhere(documentSelectionWhere)
    }
    else {
      val nodeSelectionWhere = 
        """
 		WHERE document.id IN 
	      (SELECT document_id FROM node_document WHERE node_id IN
        """ + idList(nodeIds) +
          ")"
          
        documentQueryWhere(nodeSelectionWhere)
    }
  }

  private def documentQueryWhere(where: String)(implicit c: Connection) : List[DocumentData] = {
    SQL("""
        SELECT id, title, text_url, view_url FROM document
        """ + where
        ).as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map (flatten) *)
  }
  
  private def idList(idList: List[Long]) : String = {
    idList.mkString("(", ",", ")")
  }
}