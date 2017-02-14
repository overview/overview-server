package com.overviewdocs.documentcloud

import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod}
import com.overviewdocs.util.{Configuration,Textify}

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
    Some(DocumentCloudDocumentHeader.BaseUrl + "/documents/" + documentCloudId + pageNumber.fold("")(n => s"#p${n}")),
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
    None, // bellzTODO: Should we support thumbnails on documentcloud documents
    Textify.truncateToNChars(text, DocumentCloudDocumentHeader.MaxNCharsPerDocument)
  )
}

object DocumentCloudDocumentHeader {
  private val BaseUrl = Configuration.getString("documentcloud_url")
  private val MaxNCharsPerDocument = Configuration.getInt("max_n_chars_per_document")
}
