package models.upload

import java.net.URLDecoder
import java.sql.Timestamp
import models.orm.UploadedFile
import scala.util.control.Exception._

trait OverviewUploadedFile {
  val id: Long
  val uploadedAt: Timestamp
  val contentsOid: Long
  val contentDisposition: String
  val contentType: String
  val size: Long

  def filename: String = {
    ContentDisposition.filename(contentDisposition).getOrElse("Upload " + uploadedAt)
  }

  def withSize(size: Long): OverviewUploadedFile
  def withContentInfo(contentDisposition: String, contentType: String): OverviewUploadedFile
  def save: OverviewUploadedFile
  def delete
}

object OverviewUploadedFile {
  def apply(uploadedFile: UploadedFile): OverviewUploadedFile = {
    new OverviewUploadedFileImpl(uploadedFile)
  }

  def apply(oid: Long, contentDisposition: String, contentType: String): OverviewUploadedFile = {
    val uploadedFile = UploadedFile(uploadedAt = now, contentsOid = oid, contentDisposition = contentDisposition, contentType = contentType, size = 0)
    apply(uploadedFile)
  }

  def findById(id: Long): Option[OverviewUploadedFile] = {
    UploadedFile.findById(id).map(new OverviewUploadedFileImpl(_))
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private class OverviewUploadedFileImpl(uploadedFile: UploadedFile) extends OverviewUploadedFile {
    import models.orm.Schema.uploadedFiles
    import org.overviewproject.postgres.SquerylEntrypoint._

    val id = uploadedFile.id
    val uploadedAt = uploadedFile.uploadedAt
    val contentsOid = uploadedFile.contentsOid
    val contentDisposition = uploadedFile.contentDisposition
    val contentType = uploadedFile.contentType
    val size = uploadedFile.size

    def withSize(size: Long): OverviewUploadedFile = new OverviewUploadedFileImpl(uploadedFile.copy(uploadedAt = now, size = size))

    def withContentInfo(contentDisposition: String, contentType: String): OverviewUploadedFile =
      new OverviewUploadedFileImpl(uploadedFile.copy(contentDisposition = contentDisposition, contentType = contentType))

    def save: OverviewUploadedFile = {
      uploadedFiles.insertOrUpdate(uploadedFile)
      new OverviewUploadedFileImpl(uploadedFile)
    }

    def delete {
      uploadedFiles.deleteWhere(u => u.id === id)
    }
  }
}
