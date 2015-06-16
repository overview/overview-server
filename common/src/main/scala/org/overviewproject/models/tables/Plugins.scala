package org.overviewproject.models.tables

import java.util.UUID

import org.overviewproject.database.Slick.api._
import org.overviewproject.models.Plugin

class PluginsImpl(tag: Tag) extends Table[Plugin](tag, "plugin") {
  def id = column[UUID]("id", O.PrimaryKey)
  def name = column[String]("name")
  def description = column[String]("description")
  def url = column[String]("url")
  def autocreate = column[Boolean]("autocreate")
  def autocreateOrder = column[Int]("autocreate_order")

  def * = (id, name, description, url, autocreate, autocreateOrder) <> ((Plugin.apply _).tupled, Plugin.unapply)
}

object Plugins extends TableQuery(new PluginsImpl(_))
