package org.overviewproject.tree.orm

import java.sql.Timestamp
import java.util.UUID
import org.squeryl.KeyedEntity
import org.overviewproject.util.ContentDisposition

// This class should be merged with or replace UploadedFile
case class GroupedFileUpload(
    fileGroupId: Long,
    guid: UUID,
    contentType: String,
    name: String,
    size: Long,
    uploadedSize: Long,
    contentsOid: Long,
    id: Long = 0L) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)

  def contentDisposition : String = ContentDisposition.fromFilename(name).contentDisposition
}
