package models.upload

import java.sql.Timestamp
import java.util.UUID
import models.orm.Upload

trait OverviewUpload {
  val userId: Long
  val lastActivity: Timestamp
  val bytesUploaded: Long
  val contentsOid: Long

  def withUploadedBytes(bytesUploaded: Long): OverviewUpload
  def save: OverviewUpload
}

object OverviewUpload {
  
  def apply(userId: Long, guid: UUID, filename: String, totalSize: Long, oid: Long): OverviewUpload = {
    val upload =
      Upload(userId = userId, guid = guid, lastActivity = now, filename = filename, bytesUploaded = 0,
	bytesTotal = totalSize, contentsOid = oid)
  
    new OverviewUploadImpl(upload)
  }

  def find(userId: Long, guid: UUID): Option[OverviewUpload] =
    Upload.findUserUpload(userId, guid).map(new OverviewUploadImpl(_))
  
  private class OverviewUploadImpl(upload: Upload) extends OverviewUpload {
    val userId = upload.userId
    val lastActivity = upload.lastActivity
    val bytesUploaded = upload.bytesUploaded
    val contentsOid = upload.contentsOid

    def withUploadedBytes(bytesUploaded: Long): OverviewUpload =
      new OverviewUploadImpl(upload.copy(bytesUploaded = bytesUploaded, lastActivity = now))

    def save: OverviewUpload = {
      upload.save
      this
    }
      
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis)
  
}
