package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.File
import java.util.UUID
import java.io.InputStream
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.FileHandlerProtocol.ExtractText

class FileHandlerSpec extends Specification with Mockito {

  "FileHandler" should {

    val file = File(
      UUID.randomUUID,
      "filename",
      "contentType",
      1L,
      100L,
      1L)

    val extractedText: String = "Text from PDF"

    class TestFileHandler extends FileHandler with FileHandlerComponents {

      override val dataStore = mock[DataStore]
      override val pdfProcessor = mock[PdfProcessor]

      dataStore.findFile(file.id) returns (Some(file))
      dataStore.fileContentStream(file.contentsOid) returns mock[InputStream]

      pdfProcessor.extractText(any) returns extractedText

    }

    "call components" in new ActorSystemContext {
      val fileHandler = TestActorRef(new TestFileHandler)
      
      val dataStore = fileHandler.underlyingActor.dataStore
      val pdfProcessor = fileHandler.underlyingActor.pdfProcessor
      
      fileHandler ! ExtractText(0L, file.id)
      
      there was one(dataStore).findFile(file.id)
      there was one(dataStore).fileContentStream(file.contentsOid)
      
      there was one(pdfProcessor).extractText(any)

    }
  }
}