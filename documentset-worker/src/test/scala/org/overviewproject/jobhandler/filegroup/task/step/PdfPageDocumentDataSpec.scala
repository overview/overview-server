package org.overviewproject.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject

import org.specs2.mutable.Specification
import org.overviewproject.models.{Document,DocumentDisplayMethod}

class PdfPageDocumentDataSpec extends Specification {

  "PdfPageDocumentData" should {

    "create a Document" in {
      val documentSetId = 10l
      val documentId = 9l
      val title = "title"

      val fileId = 1l
      val pageId = 2l
      val pageNumber = 13

      val displayAsPage = DocumentDisplayMethod.page
      val text = "document text"

      val document = PdfPageDocumentData(title, fileId, pageNumber, pageId, text)
        .toDocument(documentSetId, documentId)

      requiredData(document) must be equalTo (documentSetId, documentId, title, Some(fileId),
        Some(pageId), Some(pageNumber), displayAsPage, text)

      unsetData(document) must be equalTo (None, "", Seq.empty, JsObject(Seq()))
    }

    def requiredData(document: Document) =
      (document.documentSetId,
        document.id,
        document.title,
        document.fileId,
        document.pageId,
        document.pageNumber,
        document.displayMethod,
        document.text)

    def unsetData(document: Document) =
      (document.url,
        document.suppliedId,
        document.keywords,
        document.metadataJson)

  }
}
