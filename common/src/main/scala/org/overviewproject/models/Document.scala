package org.overviewproject.models

import org.overviewproject.tree.orm.{Document => DeprecatedDocument}

/** A complete Document.
  *
  * The `text` field can make this rather large. If you don't need the `text`
  * field, use a DocumentInfo object instead.
  */
case class Document(
  override val id: Long,
  override val documentSetId: Long,
  override val url: Option[String],
  override val suppliedId: String,
  override val title: String,
  override val pageNumber: Option[Int],
  override val keywords: Seq[String],
  val fileId: Option[Long],
  val pageId: Option[Long],
  val text: String
) extends DocumentHeader {
  def toDeprecatedDocument: DeprecatedDocument = DeprecatedDocument(
    documentSetId=documentSetId,
    description=keywords.mkString(" "),
    title=Some(title),
    suppliedId=Some(suppliedId),
    text=Some(text),
    url=url,
    documentcloudId=None,
    fileId=fileId,
    pageId=pageId,
    pageNumber=pageNumber,
    id=id
  )

  def toDocumentInfo: DocumentInfo = DocumentInfo(
    id,
    documentSetId,
    url,
    suppliedId,
    title,
    pageNumber,
    keywords
  )
}
