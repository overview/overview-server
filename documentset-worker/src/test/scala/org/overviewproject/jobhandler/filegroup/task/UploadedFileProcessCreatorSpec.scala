package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream

import akka.actor.ActorRef

import org.overviewproject.jobhandler.filegroup.task.DocumentTypeDetector.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.util.BulkDocumentWriter
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope



class UploadedFileProcessCreatorSpec extends Specification with Mockito {

  "UploadFileProcessCreator" should {
    
    "create a process based on document type" in new UploadedFileContext {
      val process = uploadedFileCreator.create(uploadedFile, options, documentSetId, 
          documentIdSupplier, bulkDocumentWriter)
      
      process must be equalTo(createdProcess)
    }
    
  }
  
  trait UploadedFileContext extends Scope {
    val documentSetId = 1l
    val name = "filename"
    val documentType = PdfDocument
    
    val uploadedFile = smartMock[GroupedFileUpload]
    val options = smartMock[UploadProcessOptions]
    val documentIdSupplier = smartMock[ActorRef]
    val bulkDocumentWriter = smartMock[BulkDocumentWriter]
    
    val mockTypeDetector = smartMock[DocumentTypeDetector]
    val mockStream = smartMock[InputStream]
    val createdProcess = smartMock[UploadedFileProcess]
    
    uploadedFile.name returns name
    mockTypeDetector.detect(name, mockStream) returns documentType
    

    val uploadedFileCreator = new TestUploadedFileProcessCreator
    

    class TestUploadedFileProcessCreator extends UploadedFileProcessCreator {
      override protected val documentTypeDetector = mockTypeDetector
      override protected def largeObjectInputStream(oid: Long) = mockStream
      
      override protected val processMap = smartMock[ProcessMap]

      // We can't match ActorRef directly, so need individual matchers for all parameters
      processMap.getProcess(
          be_===(documentType), 
          be_===(options),
          be_===(documentSetId),
          be_===(name), 
          any) returns createdProcess
      
    }
    
  }
}