package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader() {

  def loadSelectedDocumentSlice(nodeIds: Seq[Long], documentIds: Seq[Long],
		  						firstRow: Long, maxRows: Long)(implicit c: Connection): List[DocumentData] = {
    
    val where = createWhereClause(nodeIds, documentIds)
    documentSliceQueryWhere(firstRow, maxRows, where)
  }

  def loadCount(nodeIds: Seq[Long], documentIds: Seq[Long])(implicit c: Connection): Long = {
    val where = createWhereClause(nodeIds, documentIds)
    countQueryWhere(where)
  }
  
  private def nodeSelection(nodeIds: Seq[Long]) : String = {
    """
    document.id IN 
	  (SELECT document_id FROM node_document WHERE node_id IN """ + idList(nodeIds) + ")"
  }
  
  private def documentSelection(documentIds: Seq[Long]) : String = {
    "document.id IN " + idList(documentIds)
  }
  
  private def createWhereClause(nodeIds: Seq[Long], documentIds: Seq[Long]): String = {
    val whereClauses = List(
    		whereClauseForIds(nodeSelection(nodeIds), nodeIds),
    		whereClauseForIds(documentSelection(documentIds), documentIds)
    )    

    combineWhereClauses(whereClauses)
  }
  
  private def combineWhereClauses(whereClauses: List[Option[String]]) : String = {
    val actualWheres = whereClauses.flatMap(_.toList)
    actualWheres match {
      case Nil => ""
      case _ => actualWheres.mkString("WHERE ", " AND ", " ")
    }
  }
  
  private def whereClauseForIds(where: String, ids: Seq[Long]) : Option[String] =
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
  
  private def countQueryWhere(where: String)(implicit c: Connection) : Long = {
    SQL("""
        SELECT COUNT(*) FROM document 
        """ + where  
        ).as(scalar[Long].single)
  }
  
  
  private def idList(idList: Seq[Long]) : String = {
    idList.mkString("(", ",", ")")
  }
}