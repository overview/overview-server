package org.overviewproject.models

import java.sql.Timestamp

case class UploadedFile(
  id: Long,
  contentDisposition: String,
  contentType: String,
  size: Long,
  uploadedAt: Timestamp
)

