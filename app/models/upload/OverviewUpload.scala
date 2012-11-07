package models.upload

import java.sql.Timestamp
import java.util.UUID
import models.orm.Upload

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
  val bytesUploaded: Long
  val contentsOid: Long

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
  def apply(userId: Long, guid: UUID, filename: String, totalSize: Long, oid: Long): OverviewUpload = {
    val upload =
      Upload(userId = userId, guid = guid, lastActivity = now, filename = filename, bytesUploaded = 0,
        bytesTotal = totalSize, contentsOid = oid)

    new OverviewUploadImpl(upload)
  }

  /** Find currently existing instance */
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

    def truncate: OverviewUpload = new OverviewUploadImpl(upload.copy(bytesUploaded = 0l, lastActivity = now))

    def delete {
      upload.delete
    }
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis)

}
