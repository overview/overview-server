package com.overviewdocs.models

import java.sql.Timestamp

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

  /**
   * @return a string with the value of the charset field in contentType,
   * or None, if the contentType could not be parsed.
   */
  def encoding: Option[String] = contentType match {
    case ContentTypeEncoding(c) => Some(c)
    case _ => None
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
