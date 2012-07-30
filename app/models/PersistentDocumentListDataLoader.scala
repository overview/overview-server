package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader(nodeIds: List[Long], documentIds: List[Long]) {

  def loadDocumentSlice(firstRow: Long, maxRows: Long)(implicit c: Connection): List[DocumentData] = {
    val selectionCriteria = List(nodeIds, documentIds)
    
    val whereClauses = List(
        whereClauseForIds(nodeSelection(nodeIds), nodeIds),
        whereClauseForIds(documentSelection(documentIds), documentIds)
    )    
    documentSliceQueryWhere(firstRow, maxRows, combineWhereClauses(whereClauses))
  }

  
  private def nodeSelection(nodeIds: List[Long]) : String = {
    """
    document.id IN 
	  (SELECT document_id FROM node_document WHERE node_id IN """ + idList(nodeIds) + ")"
  }
  
  private def documentSelection(documentIds: List[Long]) : String = {
    "document.id IN " + idList(documentIds)
  }
  
  private def combineWhereClauses(whereClauses: List[Option[String]]) : String = {
    val actualWheres = whereClauses.flatMap(_.toList)
    actualWheres match {
      case Nil => ""
      case _ => actualWheres.mkString("WHERE ", " AND ", " ")
    }
    
  }
  
  private def whereClauseForIds(where: String, ids: List[Long]) : Option[String] =
    ids match {
    case Nil => None
    case _ => Some(where)
  }
  
  private def documentSliceQueryWhere(firstRow: Long, maxRows: Long, where: String)(implicit c: Connection) : List[DocumentData] = {
    SQL("""
        SELECT id, title, text_url, view_url FROM document 
        """ + where + 
        """
        ORDER BY id 
        LIMIT {maxRows} OFFSET {offset}
        """).on("maxRows" -> maxRows, "offset" -> firstRow).
        as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map (flatten) *)
  }
  
  private def idList(idList: List[Long]) : String = {
    idList.mkString("(", ",", ")")
  }
}