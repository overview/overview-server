package models.orm

import java.sql.Timestamp
import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.overviewproject.postgres.SquerylEntrypoint._
import java.util.Date


case class UploadedFile(
  id: Long = 0L,
  @Column("uploaded_at") uploadedAt: Timestamp = new Timestamp(new Date().getTime),
  @Column("contents_oid") contentsOid: Long,
  @Column("content_disposition") contentDisposition: String,
  @Column("content_type") contentType: String,
  size: Long) extends KeyedEntity[Long] {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def save: UploadedFile = Schema.uploadedFiles.insertOrUpdate(this)

  def delete { Schema.uploadedFiles.deleteWhere(u => u.id === id) }
}

object UploadedFile {
  def findById(id: Long): Option[UploadedFile] =
    Schema.uploadedFiles.where(u => u.id === id).headOption
}
