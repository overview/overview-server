package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.File
import java.util.UUID
import java.io.InputStream
import akka.testkit.{ ImplicitSender, TestActorRef }
import org.overviewproject.jobhandler.filegroup.FileHandlerProtocol.ExtractText
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.FileUpload
import java.sql.Timestamp
import org.overviewproject.jobhandler.JobDone

class FileHandlerSpec extends Specification with Mockito {

  "FileHandler" should {

    val fileUpload = FileUpload(
      1L,
      UUID.randomUUID,
      "contentDisposition",
      "contentType",
      10000L,
      new Timestamp(0L),
      100L)

    val extractedText: String = "Text from PDF"

    val file = File(
      1L,
      fileUpload.guid,
      "file name",
      fileUpload.contentType,
      fileUpload.size,
      Complete,
      extractedText,
      fileUpload.lastActivity)

    class TestFileHandler extends FileHandler with FileHandlerComponents {

      override val dataStore = mock[DataStore]
      override val pdfProcessor = mock[PdfProcessor]

      dataStore.findFileUpload(fileUpload.id) returns (Some(fileUpload))
      dataStore.fileContentStream(fileUpload.contentsOid) returns mock[InputStream]

      pdfProcessor.extractText(any) returns extractedText

    }

    "call components" in new ActorSystemContext {
      val fileHandler = TestActorRef(new TestFileHandler)

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
      val fileHandler = TestActorRef(new TestFileHandler)

      fileHandler ! ExtractText(documentSetId, fileUpload.id)

      expectMsg(JobDone(documentSetId))
    }
  }
}