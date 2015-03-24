package org.overviewproject.models

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
