package com.overviewdocs.jobhandler.filegroup.task

import java.util.Date
import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod}

case class DocumentWithoutIds(
  val url: Option[String],
  val suppliedId: String,
  val title: String,
  val pageNumber: Option[Int],
  val keywords: Seq[String],
  val createdAt: Date,
  val fileId: Option[Long],
  val pageId: Option[Long],
  val displayMethod: DocumentDisplayMethod.Value,
  val isFromOcr: Boolean,
  val metadataJson: JsObject,
  val text: String
) {
  def toDocument(documentSetId: Long, id: Long): Document = Document(
    id=id,
    documentSetId=documentSetId,
    url=url,
    suppliedId=suppliedId,
    title=title,
    pageNumber=pageNumber,
    keywords=keywords,
    createdAt=createdAt,
    fileId=fileId,
    pageId=pageId,
    displayMethod=displayMethod,
    isFromOcr=isFromOcr,
    metadataJson=metadataJson,
    text=text
  )
}
