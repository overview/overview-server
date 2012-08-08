package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection
import models.DatabaseStructure.DocumentTagData

class DocumentTagDataLoader {

  def loadDocumentTags(documentIds: Seq[Long])(implicit c: Connection) : List[DocumentTagData] = {
    documentTagQuery(documentIds)
  }
  
  protected def idList(ids: Seq[Long]) : String = "(" + ids.mkString(", ") + ")"
  
  private def documentTagQuery(documentIds: Seq[Long])(implicit c: Connection) : 
	  List[DocumentTagData] = {
    val whereDocumentIsSelected = documentIds match {
      case Nil => ""
      case _ => "WHERE document_tag.document_id IN " + idList(documentIds)
    }
    
    SQL("""
        SELECT document_tag.document_id, document_tag.tag_id 
        FROM document_tag
        INNER JOIN tag ON document_tag.tag_id = tag.id """ +
        whereDocumentIsSelected +
        """
        ORDER BY document_tag.document_id, tag.name
        """).as(long("document_id") ~ long("tag_id") map(flatten) *)
  }  
}