package com.overviewdocs.models.tables

import java.sql.Timestamp

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.Tree

class TreesImpl(tag: Tag) extends Table[Tree](tag, "tree") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def rootNodeId = column[Option[Long]]("root_node_id")
  def title = column[String]("title")
  def documentCount = column[Option[Int]]("document_count")
  def lang = column[String]("lang")
  def description = column[String]("description")
  def suppliedStopWords = column[String]("supplied_stop_words")
  def importantWords = column[String]("important_words")
  def createdAt = column[Timestamp]("created_at")
  def tagId = column[Option[Long]]("tag_id")
  def progress = column[Double]("progress")
  def progressDescription = column[String]("progress_description")
  def cancelled = column[Boolean]("cancelled")

  def * = (
    id,
    documentSetId,
    rootNodeId,
    title,
    documentCount,
    lang,
    description,
    suppliedStopWords,
    importantWords,
    createdAt,
    tagId,
    progress,
    progressDescription,
    cancelled
  ) <> ((Tree.apply _).tupled, Tree.unapply)
}

object Trees extends TableQuery(new TreesImpl(_))
