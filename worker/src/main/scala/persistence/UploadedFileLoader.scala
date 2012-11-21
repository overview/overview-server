package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

case class UploadedFile(contentsOid: Long, size: Long)

object UploadedFileLoader {

  def load(uploadedFileId: Long)(implicit c: Connection): UploadedFile = {
    val parser = long("contents_oid") ~ long("size") map {
      case contentsOid ~ size => UploadedFile(contentsOid, size)
    }

    SQL("""
        SELECT contents_oid, size FROM uploaded_file WHERE id = {uploadedFileId}
        """).on("uploadedFileId" -> uploadedFileId).as(parser.single)
  }
}