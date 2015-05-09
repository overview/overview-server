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
import org.overviewproject.searchindex.ElasticSearchIndexClient

class WriteDocumentsSpec extends Specification with NoTimeConversions with Mockito {

  "WriteDocuments" should {

    "write and index added document" in new DocumentScope {
      await(writeDocuments.execute)

      there was one(mockDocumentWriter).addAndFlushIfNeeded(mockDocument)
      there was one(mockDocumentWriter).flush
      
      there was one(mockSearchIndex).addDocuments(Seq(mockDocument))
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
    
    val mockSearchIndex = smartMock[ElasticSearchIndexClient]
    
    val writeDocuments = new TestWriteDocuments

    def mockStorage = writeDocuments.mockStorage
    def await[A](f: => Future[A]) = Await.result(f, 100 millis)

    mockDocument.fileId returns Some(fileId)
    mockDocumentWriter.addAndFlushIfNeeded(any) returns Future.successful(())
    mockDocumentWriter.flush returns Future.successful(())
    
    mockSearchIndex.addDocuments(Seq(mockDocument)) returns Future.successful(())

    protected class TestWriteDocuments extends WriteDocuments {
      override protected val documents = Seq(mockDocument)
      override protected val bulkDocumentWriter = mockDocumentWriter
      override protected val searchIndex = mockSearchIndex

      override protected val storage = smartMock[Storage]

      storage.deleteTempDocumentSetFiles(any) returns Future.successful(1)
      def mockStorage = storage
    }

  }

}