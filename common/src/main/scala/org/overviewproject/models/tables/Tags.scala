package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.{Tag=>OrmTag}

class TagsImpl(tag: Tag) extends Table[OrmTag](tag, "tag") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def name = column[String]("name")
  def color = column[String]("color")

  def * = (id, documentSetId, name, color) <> ((OrmTag.apply _).tupled, OrmTag.unapply)
}

object Tags extends TableQuery(new TagsImpl(_))
