package com.overviewdocs.jobhandler.filegroup.task

import java.io.InputStream
import org.overviewproject.mime_types.MimeTypeDetector
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import com.overviewdocs.jobhandler.filegroup.task.DocumentTypeDetector._

class DocumentTypeDetectorSpec extends Specification with Mockito {
  "DocumentTypeDetector" should {
    "detect document type" in new BaseScope {
      mockMimeTypeDetector.detectMimeType(any[String], any[InputStream]) returns "application/pdf"
      subject.detect("file.pdf", mock[InputStream]) must beEqualTo(PdfDocument)
    }

    "return UnsupportedDocument if mimetype is not handled" in new BaseScope {
      mockMimeTypeDetector.detectMimeType(any[String], any[InputStream]) returns "some-nonexistent/file-type"
      subject.detect("file.pdf", mock[InputStream]) must beEqualTo(UnsupportedDocument("some-nonexistent/file-type"))
    }
  }

  class TestDetector(override val mimeTypeDetector: MimeTypeDetector) extends DocumentTypeDetector

  trait BaseScope extends Scope {
    val mockMimeTypeDetector = smartMock[MimeTypeDetector]
    mockMimeTypeDetector.getMaxGetBytesLength returns 4
    val subject = new TestDetector(mockMimeTypeDetector)
  }
}
