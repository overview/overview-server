package com.overviewdocs.models.tables

import java.util.UUID

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.GroupedFileUpload

class GroupedFileUploadsImpl(tag: Tag) extends Table[GroupedFileUpload](tag, "grouped_file_upload") {
  def id = column[Long]("id", O.PrimaryKey)
  def fileGroupId = column[Long]("file_group_id")
  def guid = column[UUID]("guid")
  def contentType = column[String]("content_type")
  def name = column[String]("name")
  def size = column[Long]("size")
  def uploadedSize = column[Long]("uploaded_size")
  def contentsOid = column[Long]("contents_oid")
  
  def * = (id, fileGroupId, guid, contentType, name, size, uploadedSize, contentsOid) <> ((GroupedFileUpload.apply _).tupled, GroupedFileUpload.unapply)
}

object GroupedFileUploads extends TableQuery(new GroupedFileUploadsImpl(_))
