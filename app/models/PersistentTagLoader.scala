package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class PersistentTagLoader {
  
  def loadByName(name: String)(implicit c: Connection) : Option[Long] = {
    SQL("SELECT id FROM tag WHERE name = {name}").on("name" -> name).
      as(scalar[Long] *).headOption
  }

  def countDocuments(id: Long)(implicit c: Connection) : Long = {
    SQL("SELECT COUNT(*) from document_tag WHERE tag_id = {tagId}").
      on("tagId" -> id).as(scalar[Long] single)
  }
  
  def countsPerNode(nodeIds: Seq[Long], id: Long)(implicit c: Connection) : Seq[(Long, Long)] = {
//SELECT nd.node_id, dt.tag_id, COUNT(*)
//FROM node_document nd
//INNER JOIN document_tag dt ON nd.document_id = dt.document_id
//WHERE ...
//
//Am I correct in thinking that the WHERE clause is 
//WHERE dt.tag_id = {tagId} AND
//WHERE nd.node_id IN (node id list)
    SQL("""
        SELECT node_id, COUNT(*) FROM node_document
        INNER JOIN document_tag ON node_document.document_id = document_tag.document_id
        WHERE document_tag.tag_id = {tagId} AND
              node_document.node_id IN """ + nodeIds.mkString("(", ",", ")") + 
        """
        GROUP BY node_document.node_id
        """).on("tagId" -> id).as(long("node_id") ~ long("count") map(flatten) *)

  }
}