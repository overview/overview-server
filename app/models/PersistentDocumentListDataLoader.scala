package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader(nodeIds: List[Long], documentIds: List[Long]) {
  
  def loadDocumentSlice(firstRow: Long, maxRows: Long)
                       (implicit c: Connection) : List[DocumentData] = {
    if (nodeIds.isEmpty) {
	  SQL("""
          SELECT id, title, text_url, view_url FROM document
          WHERE document.id IN
	      """ + documentIds.mkString("(", ",", ")")
          ).as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map(flatten) *)
    }
    else {
	  SQL("""
          SELECT id, title, text_url, view_url FROM document
          WHERE document.id IN 
	        (SELECT document_id FROM node_document WHERE node_id IN 
          """ + nodeIds.mkString("(", ",", ")") +
          """)"""
          ).as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map(flatten) *)
    }
  }

}