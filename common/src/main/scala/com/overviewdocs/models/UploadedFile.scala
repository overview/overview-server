package com.overviewdocs.models

import java.nio.charset.{Charset,IllegalCharsetNameException}
import java.sql.Timestamp

import com.overviewdocs.util.ContentDisposition

case class UploadedFile(
  id: Long,
  contentDisposition: String,
  contentType: String,
  size: Long,
  uploadedAt: Timestamp
) {
  def toCreateAttributes = UploadedFile.CreateAttributes(
    contentDisposition,
    contentType,
    size,
    uploadedAt
  )

  private val ContentTypeEncoding = ".*charset=([^\\s]*)".r

  /** The filename, determined by Content-Disposition header.
    *
    * None on parse error.
    */
  def maybeFilename: Option[String] = ContentDisposition(contentDisposition).filename

  /** The filename, defaulting to `input.csv` on parse error. */
  def filename: String = maybeFilename.getOrElse("input.csv")

  /** The String charset, such as "utf-8".
    *
    * None if not specified or parse error.
    */
  def maybeEncoding: Option[String] = contentType match {
    case ContentTypeEncoding(c) => Some(c)
    case _ => None
  }

  /** The requested Charset, or None if the requested charset is invalid. */
  def maybeCharset: Option[Charset] = maybeEncoding match {
    case Some(encoding) => {
      try {
        Some(Charset.forName(encoding))
      } catch {
        case _: IllegalCharsetNameException => None
      }
    }
    case None => None
  }
}

object UploadedFile {
  case class CreateAttributes(
    contentDisposition: String,
    contentType: String,
    size: Long,
    uploadedAt: Timestamp
  )

  case class UpdateAttributes(
    size: Long,
    uploadedAt: Timestamp
  )
}
