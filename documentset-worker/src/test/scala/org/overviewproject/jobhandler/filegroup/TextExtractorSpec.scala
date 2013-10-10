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

class TextExtractorSpec extends Specification with Mockito {

  "TextExtractor" should {

    val fileUpload = GroupedFileUpload(
      1L,
      UUID.randomUUID,
      "contentType",
      "name",
      10000L,
      10000L,
      100L)

    val extractedText: String = "Text from PDF"

    val file = GroupedProcessedFile(
      1L,
      fileUpload.contentType,
      "file name",
      None,
      Some(extractedText),
      fileUpload.contentsOid)
      

    class TestTextExtractor extends TextExtractor with TextExtractorComponents {

      override val dataStore = mock[DataStore]
      override val pdfProcessor = mock[PdfProcessor]

      dataStore.findFileUpload(fileUpload.id) returns (Some(fileUpload))
      dataStore.fileContentStream(fileUpload.contentsOid) returns mock[InputStream]

      pdfProcessor.extractText(any) returns extractedText

    }

    "call components" in new ActorSystemContext {
      val fileHandler = TestActorRef(new TestTextExtractor)

      val dataStore = fileHandler.underlyingActor.dataStore
      val pdfProcessor = fileHandler.underlyingActor.pdfProcessor

      fileHandler ! ExtractText(0L, fileUpload.id)

      there was one(dataStore).findFileUpload(fileUpload.id)
      there was one(dataStore).fileContentStream(fileUpload.contentsOid)

      there was one(pdfProcessor).extractText(any)
      there was one(dataStore).storeFile(any) // can't check against file for some reason
    }

    "send JobDone to sender" in new ActorSystemContext {
      val documentSetId = 1l
      val fileHandler = TestActorRef(new TestTextExtractor)

      fileHandler ! ExtractText(documentSetId, fileUpload.id)

      expectMsg(JobDone(documentSetId))
    }
    
    "die after job is done" in new ActorSystemContext {
      val deathWatcher = TestProbe()
      
      val documentSetId = 1l
      val fileHandler = TestActorRef(new TestTextExtractor)
      deathWatcher.watch(fileHandler)
      
      fileHandler ! ExtractText(documentSetId, fileUpload.id)
      
      deathWatcher.expectMsgType[Terminated].actor must be(fileHandler) 
    }
  }
}