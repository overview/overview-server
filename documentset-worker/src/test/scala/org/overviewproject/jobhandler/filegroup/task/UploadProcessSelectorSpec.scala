package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfFile
import java.io.InputStream
import org.overviewproject.jobhandler.filegroup.task.DocumentTypeDetector._
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfPage
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromConvertedFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentsFromConvertedFilePages

class UploadProcessSelectorSpec extends Specification with Mockito {
  

  "UploadProcessSelector" should {

    "select CreateDocumentFromPdfFile" in new SelectionScope {
      setDocumentType(PdfDocument)

      val process = uploadProcessSelector.select(upload, documentFromFile)

      process must be equalTo CreateDocumentFromPdfFile
    }

    "select CreateDocumentFromPdfPage" in new SelectionScope {
      setDocumentType(PdfDocument)
      
      val process = uploadProcessSelector.select(upload, documentFromPage)
      
      process must be equalTo CreateDocumentFromPdfPage
    }

    "select CreateDocumentFromConvertedFile" in new SelectionScope {
      setDocumentType(OfficeDocument)

      val process = uploadProcessSelector.select(upload, documentFromFile)
      
      process must be equalTo CreateDocumentFromConvertedFile
    }

    "select CreateDocumentsFromConvertedPages" in new SelectionScope {
      setDocumentType(OfficeDocument)
      
      val process = uploadProcessSelector.select(upload, documentFromPage)
      
      process must be equalTo CreateDocumentsFromConvertedFilePages
    }

  }

  trait SelectionScope extends Scope {

    val mockDocumentTypeDetector = smartMock[DocumentTypeDetector]
    val upload = smartMock[GroupedFileUpload]
    val filename = "filename"
    upload.name returns filename
    
    val documentFromFile = UploadProcessOptions("en", false)
    val documentFromPage = UploadProcessOptions("en", true)

    val uploadProcessSelector = new TestUploadProcessSelector

    def setDocumentType(documentType: DocumentType) = mockDocumentTypeDetector.detect(be(filename), any) returns documentType

    class TestUploadProcessSelector extends UploadProcessSelector {

      override protected val documentTypeDetector = mockDocumentTypeDetector
      override protected def largeObjectInputStream(oid: Long) = smartMock[InputStream]
    }
  }

}