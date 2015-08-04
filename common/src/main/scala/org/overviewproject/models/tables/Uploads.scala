package com.overviewdocs.models.tables

import java.sql.Timestamp
import java.util.UUID

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.Upload

class UploadsImpl(tag: Tag) extends Table[Upload](tag, "upload") {
  def id = column[Long]("id", O.PrimaryKey)
  def userId = column[Long]("user_id")
  def guid = column[UUID]("guid")
  def contentsOid = column[Long]("contents_oid")
  def uploadedFileId = column[Long]("uploaded_file_id")
  def lastActivity = column[Timestamp]("last_activity")
  def totalSize = column[Long]("total_size")

  def * = (id, userId, guid, contentsOid, uploadedFileId, lastActivity, totalSize) <>
    ((Upload.apply _).tupled, Upload.unapply)

  def createAttributes = (userId, guid, contentsOid, uploadedFileId, lastActivity, totalSize) <>
    (Upload.CreateAttributes.tupled, Upload.CreateAttributes.unapply)

  def updateAttributes = (lastActivity, totalSize) <>
    (Upload.UpdateAttributes.tupled, Upload.UpdateAttributes.unapply)
}

object Uploads extends TableQuery(new UploadsImpl(_))
