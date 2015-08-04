package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod}

/** Data needed to create a [[Document]] from a page */
case class PdfPageDocumentData(title: String, fileId: Long, pageNumber: Integer, pageId: Long, text: String)
  extends DocumentData
{
  override def toDocument(documentSetId: Long, documentId: Long) = Document(
    id = documentId,
    documentSetId = documentSetId,
    title = title,
    text = text,
    fileId = Some(fileId),
    pageId = Some(pageId),
    pageNumber = Some(pageNumber),
    displayMethod = DocumentDisplayMethod.page,
    createdAt = new java.util.Date(),
    url = None,
    suppliedId = "",
    metadataJson = JsObject(Seq()),
    keywords = Seq.empty
  )
}
