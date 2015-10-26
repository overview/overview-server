package com.overviewdocs.jobhandler.filegroup.task

import java.io.{BufferedInputStream,InputStream}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import com.overviewdocs.jobhandler.filegroup.task.DocumentTypeDetector._
import org.overviewproject.mime_types.MimeTypeDetector

class DocumentTypeDetectorSpec extends Specification with Mockito {

  "DocumentTypeDetector" should {

    "detect document type" in new PdfScope {
      documentTypeDetector.detect(filename, stream) must be equalTo PdfDocument
    }

    "return UnsupportedDocument if mimetype is not handled" in new UnsupportedScope {
      documentTypeDetector.detect(filename, stream) must be equalTo UnsupportedDocument(filename, unsupportedMimeType)
    }
  }

  trait DetectorScope extends Scope {

    val mockMimeTypeDetector = smartMock[MimeTypeDetector]
    val stream = smartMock[InputStream]
    
    val filename = "file name"

    class TestDocumentTypeDetector(mimeType: String) extends DocumentTypeDetector {
      override protected val mimeTypeToDocumentType = Map("application/x-pdf" -> PdfDocument)

      override protected val mimeTypeDetector = mockMimeTypeDetector

      mimeTypeDetector.detectMimeType(be(filename), org.mockito.Matchers.isA(classOf[BufferedInputStream])) returns mimeType
      mimeTypeDetector.getMaxGetBytesLength returns 5
    }
  }

  trait PdfScope extends DetectorScope {
    val documentTypeDetector = new TestDocumentTypeDetector("application/x-pdf")
  }

  trait UnsupportedScope extends DetectorScope {
    val unsupportedMimeType = "image/png"
    val documentTypeDetector = new TestDocumentTypeDetector(unsupportedMimeType)
  }

}
