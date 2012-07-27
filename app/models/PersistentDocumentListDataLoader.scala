package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader(nodeIds: List[Long], documentIds: List[Long]) {

  def loadDocumentSlice(firstRow: Long, maxRows: Long)(implicit c: Connection): List[DocumentData] = {
    val where = List(nodeSelection(nodeIds),
      documentSelection(documentIds)).flatMap(_.toList)
      
    documentQueryWhere(where.mkString(" AND "))
  }

  private def documentSelection(documentIds: List[Long]) : Option[String] = 
    documentIds match {
    case Nil => None
    case _ => Some("document.id IN " + idList(documentIds))
  } 
  
  private def nodeSelection(nodeIds: List[Long]) : Option[String] =
    nodeIds match {
    case Nil => None
    case _ => Some("""
    			   document.id IN 
	                (SELECT document_id FROM node_document WHERE node_id IN""" + 
	                idList(nodeIds) + 
    			   ")"
    		  )
  }
  
  private def documentQueryWhere(where: String)(implicit c: Connection) : List[DocumentData] = {
    SQL("""
        SELECT id, title, text_url, view_url FROM document 
        WHERE """ + where
        ).as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map (flatten) *)
  }
  
  private def idList(idList: List[Long]) : String = {
    idList.mkString("(", ",", ")")
  }
}