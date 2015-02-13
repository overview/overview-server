package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.util.BulkDocumentWriter
import org.specs2.mock.Mockito
import org.overviewproject.models.Document
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import org.specs2.time.NoTimeConversions

class WriteDocumentsSpec extends Specification with NoTimeConversions with Mockito {
  
  "WriteDocuments" should {
    
    "write and index added document" in new DocumentScope {
      await(writeDocuments.execute)
      
      there was one(mockDocumentWriter).addAndFlushIfNeeded(mockDocument)
      there was one(mockDocumentWriter).flush
    }
    
    "delete TempDocumentSetFile" in new DocumentScope {
      await(writeDocuments.execute)
      
      there was one(mockStorage).deleteTempDocumentSetFiles(any)
    }
    
    "be the last step" in new DocumentScope {
      writeDocuments.execute must be_==(FinalStep).await
    }
  }
  
  trait DocumentScope extends Scope {
    val fileId = 1l
    val mockDocumentWriter = smartMock[BulkDocumentWriter]
    val mockDocument = smartMock[Document]
    val writeDocuments = new TestWriteDocuments(mockDocumentWriter, mockDocument)
    
    def mockStorage = writeDocuments.mockStorage
    def await[A](f: => Future[A]) = Await.result(f, 100 millis) 
      
    mockDocument.fileId returns Some(fileId)
    mockDocumentWriter.addAndFlushIfNeeded(any) returns Future.successful(())
    mockDocumentWriter.flush returns Future.successful(())
  }

  class TestWriteDocuments(val bulkDocumentWriter: BulkDocumentWriter, document: Document) extends WriteDocuments {
    override protected val documents = Seq(document)
    override protected val storage = smartMock[Storage]
    storage.deleteTempDocumentSetFiles(any) returns Future.successful(1)
    def mockStorage = storage
  } 

}