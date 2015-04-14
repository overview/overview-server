package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfFile
import java.io.InputStream

class UploadProcessSelectorSpec extends Specification with Mockito {

  "UploadProcessSelector" should {
    
    "select CreateDocumentFromPdfFile" in new SelectionScope {
      val process = uploadProcessSelector.select(upload, options)
         
      process must be equalTo(CreateDocumentFromPdfFile)
    }
    
    "select CreateDocumentFromPdfPage" in {
      todo
    }
    
    "select CreateDocumentFromConvertedFile" in {
      todo
    }
    
    "select CreateDocumentsFromConvertedPages" in {
      todo
    }
    
  }
  
  trait SelectionScope extends Scope {
    val upload = smartMock[GroupedFileUpload]
    val options = UploadProcessOptions("en", false)
    
    val uploadProcessSelector = new TestUploadProcessSelector
    
    
    class TestUploadProcessSelector extends UploadProcessSelector {
      import DocumentTypeDetector._
      
      override protected val documentTypeDetector = smartMock[DocumentTypeDetector]
      override protected def largeObjectInputStream(oid: Long) = smartMock[InputStream]
      
      documentTypeDetector.detect(any, any) returns PdfDocument
    }
  }
  
}