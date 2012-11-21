package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

case class UploadedFile(contentsOid: Long, contentType: String, size: Long) {
  private var ContentTypeEncoding = ".*charset=([^\\s]*)".r

  def encoding: Option[String] = contentType match {
    case ContentTypeEncoding(c) => Some(c)
    case _ => None
  }
}

object UploadedFileLoader {

  def load(uploadedFileId: Long)(implicit c: Connection): UploadedFile = {
    val parser = long("contents_oid") ~ str("content_type") ~ long("size") map {
      case contentsOid ~ contentType ~ size => UploadedFile(contentsOid, contentType, size)
    }

    SQL("""
        SELECT contents_oid, content_type, size FROM uploaded_file WHERE id = {uploadedFileId}
        """).on("uploadedFileId" -> uploadedFileId).as(parser.single)
  }
}