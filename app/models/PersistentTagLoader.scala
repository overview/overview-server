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
    val nodeWhere = nodeIds match {
      case Nil => ""
      case _ => " AND node_document.node_id in " + nodeIds.mkString("(", ",", ")")  
    }
    
    val nodeCounts = SQL("""
        SELECT node_id, COUNT(*) FROM node_document
        INNER JOIN document_tag ON node_document.document_id = document_tag.document_id
        WHERE document_tag.tag_id = {tagId}
        """ + nodeWhere +
        """
        GROUP BY node_document.node_id
        """).on("tagId" -> id).as(long("node_id") ~ long("count") map(flatten) *)

    val nodesWithTaggedDocuments = nodeCounts.map(_._1)
    val nodesWithNoTaggedDocuments = nodeIds.diff(nodesWithTaggedDocuments)
    val zeroCounts = nodesWithNoTaggedDocuments.map((_, 0l))
    
    nodeCounts ++ zeroCounts
  }
}