package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.Document


case class PdfFileDocumentData(title: String, fileId: Long, text: String) extends DocumentData {
  override def toDocument(documentSetId: Long, documentId: Long) = Document(
    id = documentId,
    documentSetId = documentSetId, title = title, text = text, fileId = Some(fileId),
    createdAt = new java.util.Date(),
    url = None, suppliedId = "", keywords = Seq.empty, pageNumber = None, pageId = None
  )
}
