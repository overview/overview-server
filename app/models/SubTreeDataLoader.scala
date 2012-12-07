/*
 * SubTreeDataLoader.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

import org.squeryl.Session

import DatabaseStructure._

/**
 * Utility class form SubTreeLoader that performs database queries and returns results as
 * a list of tuples.
 */
class SubTreeDataLoader extends DocumentTagDataLoader {

  
  def loadNodeTagCounts(nodeIds: Seq[Long])(implicit c: Connection): List[NodeTagCountData] = {
    nodeTagCountQuery(nodeIds)
  }

  def loadTags(documentSetId: Long)(implicit c: Connection): List[TagData] = {
    tagQuery(documentSetId)
  }

  private def nodeTagCountQuery(nodeIds: Seq[Long])(implicit c: Connection): List[NodeTagCountData] = {
    val whereNodeIsSelected = nodeIds match {
      case Nil => ""
      case _ => "WHERE node_document.node_id IN " + idList(nodeIds)
    }

    val nodeTagCountParser = long("node_id") ~ long("tag_id") ~ long("count")

    SQL("""
        SELECT node_document.node_id, document_tag.tag_id, COUNT(document_tag.tag_id)
        FROM node_document
        INNER JOIN document_tag ON node_document.document_id = document_tag.document_id """ +
      whereNodeIsSelected +
      """
        GROUP BY node_document.node_id, document_tag.tag_id
        """).as(nodeTagCountParser map (flatten) *)
  }

  private def tagQuery(documentSetId: Long)(implicit c: Connection): List[TagData] = {
    val tagDataParser = long("tag_id") ~ str("tag_name") ~ long("document_count") ~
      get[Option[Long]]("document_id") ~ get[Option[String]]("tag_color")
    SQL("""
        SELECT tag_id, tag_name, document_count, document_id, tag_color
        FROM (
          SELECT t.id AS tag_id, t.name AS tag_name,
            COUNT(dt.document_id) OVER (PARTITION BY dt.tag_id) AS document_count,
                dt.document_id, t.color AS tag_color,
            RANK() OVER (PARTITION BY dt.tag_id ORDER BY dt.document_id) AS pos
          FROM tag t
          LEFT JOIN document_tag dt ON t.id = dt.tag_id
          WHERE t.document_set_id = {documentSetId}
          ORDER BY t.name, dt.document_id
        ) ss
        WHERE ss.pos < 11
        """).on("documentSetId" -> documentSetId).
      as(tagDataParser map (flatten) *)
  }
}
