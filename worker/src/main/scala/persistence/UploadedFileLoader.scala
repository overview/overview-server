package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

object UploadedFileLoader {

  def load(uploadedFileId: Long)(implicit c: Connection): Long = {
    SQL("""
        SELECT contents_oid FROM uploaded_file WHERE id = {uploadedFileId}
        """).on("uploadedFileId" -> uploadedFileId).as(long("contents_oid") single)
        
  }
}