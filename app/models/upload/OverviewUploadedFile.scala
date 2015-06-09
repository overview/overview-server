package models.upload

import java.net.URLDecoder
import java.sql.Timestamp
import scala.util.control.Exception._

import org.overviewproject.database.BlockingDatabaseProvider
import org.overviewproject.models.UploadedFile
import org.overviewproject.models.tables.UploadedFiles
import org.overviewproject.util.ContentDisposition

trait OverviewUploadedFile {
  val id: Long
  val uploadedAt: Timestamp
  val contentDisposition: String
  val contentType: String
  val size: Long

  def filename: String = {
    ContentDisposition(contentDisposition).filename.getOrElse("Upload " + uploadedAt)
  }

  def withSize(size: Long): OverviewUploadedFile
  def withContentInfo(contentDisposition: String, contentType: String): OverviewUploadedFile
  def save: OverviewUploadedFile
  def delete
}

object OverviewUploadedFile extends BlockingDatabaseProvider {
  import org.overviewproject.database.Slick.api._

  lazy val updater = Compiled { (id: Rep[Long]) =>
    UploadedFiles.filter(_.id === id).map(_.updateAttributes)
  }

  def apply(uploadedFile: UploadedFile): OverviewUploadedFile = {
    new OverviewUploadedFileImpl(uploadedFile)
  }

  def apply(oid: Long, contentDisposition: String, contentType: String): OverviewUploadedFile = {
    // FIXME nix `oid`. (Better yet, store the contents somewhere.)
    val attributes = UploadedFile.CreateAttributes(
      uploadedAt=now,
      contentDisposition=contentDisposition,
      contentType=contentType,
      size=0
    )
    val q = (UploadedFiles.map(_.createAttributes) returning UploadedFiles).+=(attributes)
    val uploadedFile = blockingDatabase.run(q)
    new OverviewUploadedFileImpl(uploadedFile)
  }

  def findById(id: Long): Option[OverviewUploadedFile] = {
    blockingDatabase.option(UploadedFiles.filter(_.id === id))
      .map(new OverviewUploadedFileImpl(_))
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private class OverviewUploadedFileImpl(uploadedFile: UploadedFile) extends OverviewUploadedFile {
    val id = uploadedFile.id
    val uploadedAt = uploadedFile.uploadedAt
    val contentDisposition = uploadedFile.contentDisposition
    val contentType = uploadedFile.contentType
    val size = uploadedFile.size

    def withSize(size: Long): OverviewUploadedFile = new OverviewUploadedFileImpl(uploadedFile.copy(uploadedAt = now, size = size))

    def withContentInfo(contentDisposition: String, contentType: String): OverviewUploadedFile =
      new OverviewUploadedFileImpl(uploadedFile.copy(contentDisposition = contentDisposition, contentType = contentType))

    def save: OverviewUploadedFile = {
      val q = updater(id).update(UploadedFile.UpdateAttributes(size, uploadedAt))
      blockingDatabase.run(q)
      this
    }

    def delete {
      val q = UploadedFiles.filter(_.id === id).delete
      blockingDatabase.run(q)
    }
  }
}
