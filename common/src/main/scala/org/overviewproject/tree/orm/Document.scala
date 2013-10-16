package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import org.squeryl.annotations.Column
import org.overviewproject.postgres.PostgresqlEnum

case class Document(
  @Column("document_set_id") val documentSetId: Long = 0L,
  val description: String = "",
  val title: Option[String] = None,
  @Column("supplied_id") val suppliedId: Option[String] = None,
  val text: Option[String] = None,
  val url: Option[String] = None,
  @Column("documentcloud_id") val documentcloudId: Option[String] = None,
  val contentsOid: Option[Long] = None,
  val contentLength: Option[Long] = None,
  override val id: Long = 0L) extends KeyedEntity[Long] {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)
}

