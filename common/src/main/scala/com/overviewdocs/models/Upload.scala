package com.overviewdocs.models

import java.sql.Timestamp
import java.util.UUID

case class Upload(
  id: Long,
  userId: Long,
  guid: UUID,
  contentsOid: Long,
  uploadedFileId: Long,
  lastActivity: Timestamp,
  totalSize: Long
)

object Upload {
  case class CreateAttributes(
    userId: Long,
    guid: UUID,
    contentsOid: Long,
    uploadedFileId: Long,
    lastActivity: Timestamp,
    totalSize: Long
  )

  case class UpdateAttributes(
    lastActivity: Timestamp,
    totalSize: Long
  )
}
