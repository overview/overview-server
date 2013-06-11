/*
 * SubTreeDataLoader.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package models

import scala.language.postfixOps
import anorm._
import anorm.SqlParser._
import java.sql.Connection

import org.squeryl.Session

import DatabaseStructure._

/**
 * Utility class form SubTreeLoader that performs database queries and returns results as
 * a list of tuples.
 */
class SubTreeDataLoader {
  def loadNodeTagCounts(nodeIds: Seq[Long])(implicit c: Connection): List[NodeTagCountData] = {
    nodeTagCountQuery(nodeIds)
  }

  private def idList(ids: Seq[Long]): String = "(" + ids.mkString(", ") + ")"

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
}
