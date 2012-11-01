package models.upload

import java.sql.Timestamp
import java.util.UUID
import models.OverviewUser
import models.orm.Upload

trait OverviewUpload {
  val userId: Long
  val lastActivity: Timestamp
  val bytesUploaded: Long
  val contentsOid: Long

  def withUploadedBytes(bytesUploaded: Long): OverviewUpload
}

object OverviewUpload {

  def apply(user: OverviewUser, uuid: UUID, filename: String, totalSize: Long, oid: Long): OverviewUpload = {
    val upload =
      Upload(userId = user.id, guid = uuid, lastActivity = now, filename = filename, bytesUploaded = 0,
	bytesTotal = totalSize, contentsOid = oid)
  
    new OverviewUploadImpl(upload)
  }
  
  private class OverviewUploadImpl(upload: Upload) extends OverviewUpload {
    val userId = upload.userId
    val lastActivity = upload.lastActivity
    val bytesUploaded = upload.bytesUploaded
    val contentsOid = upload.contentsOid

    def withUploadedBytes(bytesUploaded: Long): OverviewUpload =
      new OverviewUploadImpl(upload.copy(bytesUploaded = bytesUploaded, lastActivity = now))
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis)
  
}
