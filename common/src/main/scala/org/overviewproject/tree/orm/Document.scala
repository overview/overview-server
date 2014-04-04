package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import org.squeryl.annotations.Column
import org.overviewproject.postgres.PostgresqlEnum

case class Document(
  val documentSetId: Long = 0L,
  val description: String = "",
  val title: Option[String] = None,
  val suppliedId: Option[String] = None,
  val text: Option[String] = None,
  val url: Option[String] = None,
  val documentcloudId: Option[String] = None,
  val contentLength: Option[Long] = None,
  val fileId: Option[Long] = None,
  val pageId: Option[Long] = None,
  val pageNumber: Option[Int] = None,
  val pageContentLength: Option[Long] = None,
  override val id: Long = 0L) extends KeyedEntity[Long] with DocumentSetComponent {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)
}

