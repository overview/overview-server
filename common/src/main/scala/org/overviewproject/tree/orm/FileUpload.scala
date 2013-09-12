package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity
import java.util.UUID

// This class should be merged with or replace UploadedFile
case class FileUpload(
    fileGroupId: Long,
    guid: UUID,
    contentDisposition: String,
    contentType: String,
    size: Long,
    lastActivity: Timestamp,
    contentsOid: Long,
    id: Long = 0L) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)

}