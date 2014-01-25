package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity
import org.overviewproject.postgres.SquerylEntrypoint._
import java.util.Date

case class UploadedFile(
    contentDisposition: String,
    contentType: String,
    size: Long,
    uploadedAt: Timestamp = new Timestamp(new Date().getTime),
    id: Long = 0L) extends KeyedEntity[Long] {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)
}
