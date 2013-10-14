package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.GroupedProcessedFile
import java.util.UUID
import java.io.InputStream
import akka.testkit.{ ImplicitSender, TestActorRef }
import org.overviewproject.jobhandler.filegroup.TextExtractorProtocol.ExtractText
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.GroupedFileUpload
import java.sql.Timestamp
import org.overviewproject.jobhandler.JobProtocol._
import akka.testkit.TestProbe
import akka.actor.Terminated
import org.specs2.mutable.Before

class TextExtractorSpec extends Specification with Mockito {

  "TextExtractor" should {

    class TestTextExtractor(foundFileUpload: Option[GroupedFileUpload], extractedText: String) extends TextExtractor with TextExtractorComponents {
      override val dataStore = smartMock[DataStore]
      override val pdfProcessor = smartMock[PdfProcessor]

      dataStore.findFileUpload(any) returns foundFileUpload
      dataStore.fileContentStream(any) returns mock[InputStream]

      pdfProcessor.extractText(any) returns extractedText

    }

    trait UploadSetup extends ActorSystemContext with Before {
      val documentSetId = 1l
      
      val fileUploadId = 1l
      val contentsOid = 10l
      val extractedText: String = "Text from PDF"

      var fileHandler: TestActorRef[TestTextExtractor] = _
      val foundFileUpload: Option[GroupedFileUpload]

      def before = {
        fileHandler = TestActorRef(new TestTextExtractor(foundFileUpload, extractedText))
      }
    }

    trait ValidUpload extends UploadSetup {
      val fileUpload = smartMock[GroupedFileUpload]
      fileUpload.id returns fileUploadId
      fileUpload.contentsOid returns contentsOid

      override val foundFileUpload = Some(fileUpload)

    }

    trait CancelledUpload extends UploadSetup {
      override val foundFileUpload = None
    }

    "call components" in new ValidUpload {

      val dataStore = fileHandler.underlyingActor.dataStore
      val pdfProcessor = fileHandler.underlyingActor.pdfProcessor

      fileHandler ! ExtractText(documentSetId, fileUploadId)

      there was one(dataStore).findFileUpload(fileUploadId)
      there was one(dataStore).fileContentStream(contentsOid)

      there was one(pdfProcessor).extractText(any)
      there was one(dataStore).storeFile(any) // can't check against file for some reason
    }

    "send JobDone to sender" in new ValidUpload {

      fileHandler ! ExtractText(documentSetId, fileUploadId)
      expectMsg(JobDone(documentSetId))
    }

    "die after job is done" in new ValidUpload {
      val deathWatcher = TestProbe()

      deathWatcher.watch(fileHandler)

      fileHandler ! ExtractText(documentSetId, fileUploadId)

      deathWatcher.expectMsgType[Terminated].actor must be(fileHandler)
    }
    
    "ignore cancelled uploads" in new CancelledUpload {
      val storage = fileHandler.underlyingActor.dataStore
      
      fileHandler ! ExtractText(documentSetId, fileUploadId)
      
      there was no(storage).fileContentStream(any)
      expectMsg(JobDone(documentSetId))
    }
  }
}