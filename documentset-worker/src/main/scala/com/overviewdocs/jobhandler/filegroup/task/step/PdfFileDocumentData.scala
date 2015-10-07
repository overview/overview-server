package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod}

/** Data needed to create a [[Document]] from a PDF [[File]] */
case class PdfFileDocumentData(title: String, fileId: Long, text: String) extends DocumentData {
  override def toDocument(documentSetId: Long, documentId: Long) = Document(
    id = documentId,
    documentSetId = documentSetId,
    url = None,
    suppliedId = "",
    title = title,
    pageNumber = None,
    keywords = Seq.empty, 
    createdAt = new java.util.Date(),
    fileId = Some(fileId),
    pageId = None,
    displayMethod = DocumentDisplayMethod.pdf,
    isFromOcr = false,
    metadataJson = JsObject(Seq()),
    text = text
  )
}
