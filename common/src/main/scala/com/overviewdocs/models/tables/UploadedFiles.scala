package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.UploadedFile
import java.sql.Timestamp

class UploadedFilesImpl(tag: Tag) extends Table[UploadedFile](tag, "uploaded_file") {

  def id = column[Long]("id", O.PrimaryKey)
  def contentDisposition = column[String]("content_disposition")
  def contentType = column[String]("content_type")
  def size = column[Long]("size")
  def uploadedAt = column[Timestamp]("uploaded_at")

  def * = (id, contentDisposition, contentType, size, uploadedAt) <>
    ((UploadedFile.apply _).tupled, UploadedFile.unapply)

  def createAttributes = (contentDisposition, contentType, size, uploadedAt) <>
    (UploadedFile.CreateAttributes.tupled, UploadedFile.CreateAttributes.unapply)

  def updateAttributes = (size, uploadedAt) <>
    (UploadedFile.UpdateAttributes.tupled, UploadedFile.UpdateAttributes.unapply)
}

object UploadedFiles extends TableQuery(new UploadedFilesImpl(_))
