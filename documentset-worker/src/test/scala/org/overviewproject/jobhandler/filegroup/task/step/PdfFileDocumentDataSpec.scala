package org.overviewproject.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject

import org.specs2.mutable.Specification
import org.overviewproject.models.{Document,DocumentDisplayMethod}

class PdfFileDocumentDataSpec extends Specification {

  "PdfFileDocumentData" should {

    "create a Document" in {
      val documentSetId = 10l
      val documentId = 9l
      val title = "title"
      val fileId = 1l
      val displayAsPdf = DocumentDisplayMethod.pdf 
      val text = "document text"

      val document = PdfFileDocumentData(title, fileId, text).toDocument(documentSetId, documentId)

      requiredData(document) must beEqualTo(documentSetId, documentId, title, Some(fileId), displayAsPdf, text)
      unsetData(document) must beEqualTo(None, "", None, Seq.empty, None, JsObject(Seq()))
    }

    def requiredData(document: Document) =
      (document.documentSetId,
        document.id,
        document.title,
        document.fileId,
        document.displayMethod,
        document.text)

    def unsetData(document: Document) =
      (document.url,
        document.suppliedId,
        document.pageNumber,
        document.keywords,
        document.pageId,
        document.metadataJson)

  }
}
