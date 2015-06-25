package org.overviewproject.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject

import org.overviewproject.models.{Document,DocumentDisplayMethod}

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
    metadataJson = JsObject(Seq()),
    text = text
  )
}
