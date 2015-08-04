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
