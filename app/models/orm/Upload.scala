package models.orm

import java.sql.Timestamp
import java.util.UUID
import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._


case class Upload(
  id: Long = 0L,
  @Column("user_id") userId: Long,
  guid: UUID,
  @Column("last_activity") lastActivity: Timestamp,
  filename: String,
  @Column("bytes_uploaded") bytesUploaded: Long,
  @Column("bytes_total") bytesTotal: Long,
  @Column("contents_oid") contentsOid: Long
  ) extends KeyedEntity[Long] {

  def save: Upload = {
    if (id == 0l) {
      Schema.uploads.insert(this)
    }
    else {
      Schema.uploads.update(this)
    }
    this
  }
  
  def delete {
    Schema.uploads.deleteWhere(u => u.userId === userId and u.guid === guid)
  }
}


object Upload {

  def findUserUpload(userId: Long, uploadId: UUID): Option[Upload] = {
    Schema.uploads.where(u => u.userId === userId and u.guid === uploadId).headOption
  }
}
