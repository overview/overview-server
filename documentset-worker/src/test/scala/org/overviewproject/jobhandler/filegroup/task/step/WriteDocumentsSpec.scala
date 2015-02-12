package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.util.BulkDocumentWriter
import org.specs2.mock.Mockito
import org.overviewproject.models.Document
import scala.concurrent.Future

class WriteDocumentsSpec extends Specification with Mockito {
  
  "WriteDocuments" should {
    
    "write and index added document" in new DocumentScope {
      writeDocuments.execute
      
      there was one(mockDocumentWriter).addAndFlushIfNeeded(mockDocument)
      there was one(mockDocumentWriter).flush
    }
    
    "be the last step" in new DocumentScope {
      writeDocuments.execute must be_==(FinalStep).await
    }
  }
  
  trait DocumentScope extends Scope {
    val mockDocumentWriter = smartMock[BulkDocumentWriter]
    val mockDocument = smartMock[Document]
    val writeDocuments = new TestWriteDocuments(mockDocumentWriter, mockDocument)
    
    mockDocumentWriter.addAndFlushIfNeeded(any) returns Future.successful(())
    mockDocumentWriter.flush returns Future.successful(())
  }

  class TestWriteDocuments(val bulkDocumentWriter: BulkDocumentWriter, document: Document) extends WriteDocuments {
    override protected val documents = Seq(document)
  } 

}