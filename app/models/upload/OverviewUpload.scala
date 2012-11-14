package models.upload

import java.sql.Timestamp
import java.util.UUID
import models.orm.Upload
import models.orm.UploadedFile

/**
 * Wrapper around models.orm.Upload hiding details of storage and managing
 * timestamp information.
 * Currently the Large Object referenced by oid is managed separately, and
 * client code needs to make sure that OverviewUpload.bytesUploaded is
 * consistent.
 */
trait OverviewUpload {
  val userId: Long
  val lastActivity: Timestamp
  val size: Long
  val uploadedFile: OverviewUploadedFile

  /** @return a copy with bytesUploaded updated to the new value */
  def withUploadedBytes(bytesUploaded: Long): OverviewUpload

  /** Store the current state in the database */
  def save: OverviewUpload

  /** Set bytesUploaded to 0 */
  def truncate: OverviewUpload

  /** Delete upload info */
  def delete
}

object OverviewUpload {

  /** Create a new instance */
  def apply(userId: Long, guid: UUID, contentDisposition: String, contentType: String, totalSize: Long, oid: Long): OverviewUpload = {
    val uploadedFile = OverviewUploadedFile(oid, contentDisposition, contentType).save
    val upload =
      Upload(userId = userId, guid = guid, uploadedFileId = uploadedFile.id, lastActivity = now, totalSize = totalSize)

    new OverviewUploadImpl(upload, uploadedFile)
  }

  /** Find currently existing instance */
  def find(userId: Long, guid: UUID): Option[OverviewUpload] =
    Upload.findUserUpload(userId, guid).flatMap { u =>
      val uploadedFile = OverviewUploadedFile.findById(u.uploadedFileId)
      uploadedFile.map(new OverviewUploadImpl(u, _))
    }

  private class OverviewUploadImpl(upload: Upload, val uploadedFile: OverviewUploadedFile) extends OverviewUpload {
    val userId = upload.userId
    val lastActivity = upload.lastActivity
    val size = upload.totalSize
 
    def withUploadedBytes(bytesUploaded: Long): OverviewUpload =
      new OverviewUploadImpl(upload.copy(lastActivity = now), uploadedFile.withSize(bytesUploaded))

    def save: OverviewUpload = {
      uploadedFile.save
      upload.save
      this
    }

    def truncate: OverviewUpload = new OverviewUploadImpl(upload.copy(lastActivity = now), uploadedFile.withSize(0l))

    def delete {
      upload.delete
    }
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis)

}
