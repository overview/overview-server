package models.orm

import java.sql.Timestamp
import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import java.util.Date


case class UploadedFile(
  id: Long = 0L,
  @Column("uploaded_at") uploadedAt: Timestamp = new Timestamp(new Date().getTime),
  @Column("contents_oid") contentsOid: Long,
  @Column("content_disposition") contentDisposition: String,
  @Column("content_type") contentType: String,
  size: Long) extends KeyedEntity[Long] {

  def save: UploadedFile = {
    if (id == 0l) {
      Schema.uploadedFiles.insert(this)
    }
    else {
      Schema.uploadedFiles.update(this)
    }
    this
  }
  
  def delete { Schema.uploadedFiles.deleteWhere(u => u.id === id) }
}

object UploadedFile {
  def findById(id: Long): Option[UploadedFile] =
    Schema.uploadedFiles.where(u => u.id === id).headOption
}