package org.overviewproject.models

import java.util.Date
import play.api.libs.json.JsObject

import org.overviewproject.models.DocumentDisplayMethod.DocumentDisplayMethod

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
  override val metadataJson: JsObject,
  override val text: String
) extends DocumentHeader {
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
