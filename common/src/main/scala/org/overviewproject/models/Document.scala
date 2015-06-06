package org.overviewproject.models

import java.util.Date
import org.overviewproject.models.DocumentDisplayMethod.DocumentDisplayMethod
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
  override val createdAt: Date,
  val fileId: Option[Long],
  val pageId: Option[Long],
  override val displayMethod: Option[DocumentDisplayMethod],
  override val text: String
) extends DocumentHeader {
  def toDeprecatedDocument: DeprecatedDocument = DeprecatedDocument(
    documentSetId=documentSetId,
    description=keywords.mkString(" "),
    title=Some(title),
    suppliedId=Some(suppliedId),
    text=Some(text),
    url=url,
    documentcloudId=None,
    createdAt=new java.sql.Timestamp(createdAt.getTime),
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
    keywords,
    createdAt,
    displayMethod,
    fileId.isDefined
  )

  override def viewUrl: Option[String] = {
    url
      .orElse(fileId.map(_ => s"/documents/${id}.pdf"))
  }
}
