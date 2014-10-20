package org.overviewproject.models.tables

import java.util.UUID

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Plugin

class PluginsImpl(tag: Tag) extends Table[Plugin](tag, "plugin") {
  def id = column[UUID]("id", O.PrimaryKey)
  def name = column[String]("name")
  def description = column[String]("description")
  def url = column[String]("url")

  def * = (id, name, description, url) <> ((Plugin.apply _).tupled, Plugin.unapply)
}

object Plugins extends TableQuery(new PluginsImpl(_))
