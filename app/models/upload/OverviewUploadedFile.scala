package models.upload

import java.sql.Timestamp
import models.orm.UploadedFile

trait OverviewUploadedFile {
  val id: Long
  val uploadedAt: Timestamp
  val contentsOid: Long
  val contentDisposition: String
  val contentType: String
  val size: Long

  def filename: String = {
    val Token = """[^\s()<>,;:\\"\/\[\]?=]+"""
    val ConDisp = """\s*%s\s*""".format(Token)
    val BrokenUnquoted = """^%s.*;\sfilename=(%s)""".format(ConDisp, Token).r
    
    contentDisposition match {
      case BrokenUnquoted(f) => f
      case _ => ""
    }
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
    val id = uploadedFile.id
    val uploadedAt = uploadedFile.uploadedAt
    val contentsOid = uploadedFile.contentsOid
    val contentDisposition = uploadedFile.contentDisposition
    val contentType = uploadedFile.contentType
    val size = uploadedFile.size

    def withSize(size: Long): OverviewUploadedFile = new OverviewUploadedFileImpl(uploadedFile.copy(uploadedAt = now, size = size))

    def withContentInfo(contentDisposition: String, contentType: String): OverviewUploadedFile =
      new OverviewUploadedFileImpl(uploadedFile.copy(contentDisposition = contentDisposition, contentType = contentType))

    def save: OverviewUploadedFile = new OverviewUploadedFileImpl(uploadedFile.save)

    def delete { uploadedFile.delete }
  }
}
