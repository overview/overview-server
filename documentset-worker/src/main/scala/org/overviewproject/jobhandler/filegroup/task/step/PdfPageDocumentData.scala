package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.Document


/** Data needed to create a [[Document]] from a page */
case class PdfPageDocumentData(title: String, fileId: Long, pageNumber: Integer, pageId: Long, text: String)
  extends DocumentData {

  override def toDocument(documentSetId: Long, documentId: Long) = Document(
    id = documentId,
    documentSetId = documentSetId, title = title, text = text,
    fileId = Some(fileId), pageId = Some(pageId), pageNumber = Some(pageNumber),
    displayMethod = None,
    createdAt = new java.util.Date(),
    url = None, suppliedId = "", keywords = Seq.empty)
}