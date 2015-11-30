package com.overviewdocs.documentcloud

import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.models.DocumentProcessingError
import com.overviewdocs.models.tables.DocumentProcessingErrors
import com.overviewdocs.test.factories.PodoFactory
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.util.BulkDocumentWriter

class DocumentWriterSpec extends DbSpecification with Mockito {
  trait BaseScope extends DbScope {
    val documentSet = factory.documentSet()
    val dcImport = factory.documentCloudImport(documentSetId=documentSet.id)
    val bulkDocumentWriter = smartMock[BulkDocumentWriter]
    bulkDocumentWriter.flush returns Future.successful(())

    var lastProgress: Int = -1
    def updateProgress(nWritten: Int) = {
      lastProgress = nWritten
      Future.successful(())
    }

    val subject = new DocumentWriter(dcImport, updateProgress, bulkDocumentWriter, 1)
  }

  "DocumentWriter" should {
    "write documents" in new BaseScope {
      subject.flushPeriodically
      val document = PodoFactory.document(documentSetId=documentSet.id)
      subject.addDocument(document)
      subject.stop must beEqualTo(()).await

      there was one(bulkDocumentWriter).add(document)
      there was atLeastOne(bulkDocumentWriter).flush
    }

    "write errors" in new BaseScope {
      subject.flushPeriodically
      subject.addError("dc1", "some problem")
      subject.stop must beEqualTo(()).await

      import database.api._
      blockingDatabase.seq(DocumentProcessingErrors.map(_.createAttributes)) must beEqualTo(Seq(
        DocumentProcessingError.CreateAttributes(documentSet.id, "dc1", "some problem", None, None)
      ))
    }

    "update progress on error" in new BaseScope {
      subject.flushPeriodically
      subject.addError("dc1", "some problem")
      subject.stop must beEqualTo(()).await

      lastProgress must beEqualTo(1)
    }

    "update progress on write" in new BaseScope {
      subject.flushPeriodically
      subject.addDocument(PodoFactory.document(documentSetId=documentSet.id))
      subject.stop must beEqualTo(()).await

      lastProgress must beEqualTo(1)
    }

    "update progress on skip" in new BaseScope {
      subject.flushPeriodically
      subject.skip(10)
      subject.stop must beEqualTo(()).await

      lastProgress must beEqualTo(10)
    }

    "add all progress" in new BaseScope {
      subject.flushPeriodically
      subject.skip(10)
      subject.addDocument(PodoFactory.document(documentSetId=documentSet.id))
      subject.addError("a", "b")
      subject.stop must beEqualTo(()).await

      lastProgress must beEqualTo(12)
    }
  }
}
