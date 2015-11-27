package com.overviewdocs.documentcloud

import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod}

case class DocumentCloudDocumentHeader(
  suppliedId: String,
  title: String,
  pageNumber: Option[Int],
  url: String,
  access: String
) {
  def toDocument(id: Long, documentSetId: Long, text: String): Document = Document(
    id,
    documentSetId,
    Some(url),
    suppliedId,
    title,
    pageNumber,
    Seq(),
    new java.util.Date(),
    None,
    None,
    DocumentDisplayMethod.auto,
    false,
    JsObject(Seq()),
    text
  )
}
