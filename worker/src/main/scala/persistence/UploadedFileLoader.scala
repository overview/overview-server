/*
 * UploadedFileLoader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

/** Information describing an uploaded file */
case class UploadedFile(contentsOid: Long, contentType: String, size: Long) {
  private var ContentTypeEncoding = ".*charset=([^\\s]*)".r 

  /**
   * @return a string with the value of the charset field in contentType,
   * or None, if the contentType could not be parsed.
   */
  def encoding: Option[String] = contentType match {
    case ContentTypeEncoding(c) => Some(c)
    case _ => None
  }
}

/** Helper for loading an UploadedFile from the database */
object UploadedFileLoader {

  /** @return the UploadedFile specified by the uploadedFileId */
  def load(uploadedFileId: Long)(implicit c: Connection): UploadedFile = {
    val parser = long("contents_oid") ~ str("content_type") ~ long("size") map {
      case contentsOid ~ contentType ~ size => UploadedFile(contentsOid, contentType, size)
    }
    // Optimistically assume that the uploadedFileId is valid.
    SQL("""
        SELECT contents_oid, content_type, size FROM uploaded_file WHERE id = {uploadedFileId}
        """).on("uploadedFileId" -> uploadedFileId).as(parser.single)
  }
}