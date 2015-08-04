package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.FileGroup

class FileGroupsImpl(tag: Tag) extends Table[FileGroup](tag, "file_group") {
  def id = column[Long]("id", O.PrimaryKey)
  def userEmail = column[String]("user_email")
  def apiToken = column[Option[String]]("api_token")
  def completed = column[Boolean]("completed")
  def deleted = column[Boolean]("deleted")
  
  def * = (id, userEmail, apiToken, completed, deleted) <> ((FileGroup.apply _).tupled, FileGroup.unapply)
}

object FileGroups extends TableQuery(new FileGroupsImpl(_))
