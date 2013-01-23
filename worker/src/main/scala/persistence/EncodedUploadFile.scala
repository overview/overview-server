/*
 * UploadedFileLoader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package persistence

import java.sql.Connection

/** Information describing an uploaded file */
case class EncodedUploadFile(contentsOid: Long, contentType: String, size: Long) {
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
object EncodedUploadFile {

  /** @return the UploadedFile specified by the uploadedFileId */
  def load(uploadedFileId: Long)(implicit c: Connection): EncodedUploadFile = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    val upload = Schema.uploadedFiles.lookup(uploadedFileId).get

    EncodedUploadFile(upload.contentsOid, upload.contentType, upload.size)
  }
}