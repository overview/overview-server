package org.overviewproject.models.tables

import java.sql.Timestamp

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Tree

class TreesImpl(tag: Tag) extends Table[Tree](tag, "tree") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def rootNodeId = column[Long]("root_node_id")
  def jobId = column[Long]("job_id")
  def title = column[String]("title")
  def documentCount = column[Int]("document_count")
  def lang = column[String]("lang")
  def description = column[String]("description")
  def suppliedStopWords = column[String]("supplied_stop_words")
  def importantWords = column[String]("important_words")
  def createdAt = column[Timestamp]("created_at")

  def * = (id, documentSetId, rootNodeId, jobId, title, documentCount, lang, description, suppliedStopWords, importantWords, createdAt) <> ((Tree.apply _).tupled, Tree.unapply)
}

object Trees extends TableQuery(new TreesImpl(_))
