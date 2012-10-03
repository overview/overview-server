package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection
import models.DatabaseStructure.{ DocumentData, DocumentNodeData, DocumentTagData }

class DocumentTagDataLoader {

  def loadDocumentTags(documentIds: Seq[Long])(implicit c: Connection): List[DocumentTagData] = {
    documentTagQuery(documentIds)
  }

  /**
   * @ return a list of tuples: (documentId, title, textUrl, viewUrl) for each documentId.
   */
  def loadDocuments(documentIds: Seq[Long])(implicit connection: Connection): List[DocumentData] = {
    documentIds match {
      case Nil => Nil
      case _ => documentQuery(documentIds)
    }
  }

  def loadNodes(documentIds: Seq[Long])(implicit connection: Connection): List[DocumentNodeData] = {
    documentNodeQuery(documentIds)
  }
    

  protected def idList(ids: Seq[Long]): String = "(" + ids.mkString(", ") + ")"

  private def documentTagQuery(documentIds: Seq[Long])(implicit c: Connection): List[DocumentTagData] = {
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
        """).as(long("document_id") ~ long("tag_id") map (flatten) *)
  }

  private def documentQuery(documentIds: Seq[Long])(implicit c: Connection): List[DocumentData] = {
    val documentParser = long("id") ~ str("title") ~ str("documentcloud_id")

    SQL(
      """
        SELECT id, title, documentcloud_id
        FROM document
        WHERE id IN
      """ + idList(documentIds)).
      as(documentParser map (flatten) *)
  }

  private def documentNodeQuery(documentIds: Seq[Long])(implicit c: Connection): List[DocumentNodeData] = {
    SQL("""
      SELECT document_id, node_id FROM node_document
      WHERE document_id IN
      """ + idList(documentIds)).as(long("document_id") ~ long("node_id") map(flatten) *)
  }
}
