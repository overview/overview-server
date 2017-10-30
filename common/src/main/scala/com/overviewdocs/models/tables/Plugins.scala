package com.overviewdocs.models.tables

import java.util.UUID

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.Plugin

class PluginsImpl(tag: Tag) extends Table[Plugin](tag, "plugin") {
  def id = column[UUID]("id", O.PrimaryKey)
  def name = column[String]("name")
  def description = column[String]("description")
  def url = column[String]("url")
  def serverUrlFromPlugin = column[Option[String]]("server_url_from_plugin")
  def autocreate = column[Boolean]("autocreate")
  def autocreateOrder = column[Int]("autocreate_order")

  def * = (id, name, description, url, serverUrlFromPlugin, autocreate, autocreateOrder) <> ((Plugin.apply _).tupled, Plugin.unapply)
}

object Plugins extends TableQuery(new PluginsImpl(_))
