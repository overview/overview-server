package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity

import org.overviewproject.models.{DocumentInfo,Document => BetterDocument}

case class Document(
  val documentSetId: Long = 0L,
  val description: String = "",
  val title: Option[String] = None,
  val suppliedId: Option[String] = None,
  val text: Option[String] = None,
  val url: Option[String] = None,
  val documentcloudId: Option[String] = None,
  val fileId: Option[Long] = None,
  val pageId: Option[Long] = None,
  val pageNumber: Option[Int] = None,
  override val id: Long = 0L) extends KeyedEntity[Long] with DocumentSetComponent {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def toDocumentInfo = DocumentInfo(
    id=id,
    documentSetId=documentSetId,
    url=url,
    suppliedId=suppliedId.orElse(documentcloudId).getOrElse(""),
    title=title.getOrElse(""),
    pageNumber=pageNumber,
    keywords=description.split(" ")
  )

  def toDocument = BetterDocument(
    id=id,
    documentSetId=documentSetId,
    url=url,
    suppliedId=suppliedId.orElse(documentcloudId).getOrElse(""),
    title=title.getOrElse(""),
    pageNumber=pageNumber,
    keywords=description.split(" "),
    fileId=fileId,
    pageId=pageId,
    text=text.getOrElse("")
  )
}

