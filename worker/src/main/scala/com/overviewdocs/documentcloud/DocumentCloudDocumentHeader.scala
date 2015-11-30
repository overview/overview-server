package com.overviewdocs.documentcloud

import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod}
import com.overviewdocs.util.Configuration

/** A yet-to-be-fetched Document.
  *
  * We precompute document IDs, so that when we resume we can skip
  * already-fetched documents with a mere ID check.
  */
case class DocumentCloudDocumentHeader(
  /** ID we're going to write to our database. */
  id: Long,

  documentSetId: Long,
  documentCloudId: String,
  title: String,
  pageNumber: Option[Int],
  textUrl: String,
  access: String
) {
  def toDocument(text: String): Document = Document(
    id,
    documentSetId,
    Some(DocumentCloudDocumentHeader.baseUrl + "/documents/" + documentCloudId + pageNumber.fold("")(n => s"#p${n}")),
    documentCloudId,
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

object DocumentCloudDocumentHeader {
  private val baseUrl = Configuration.getString("documentcloud_url")
}
