package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.ShellRunner
import scala.concurrent.duration.DurationInt
import java.awt.image.BufferedImage
import org.specs2.time.NoTimeConversions
import java.io.File
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class TesseractOcrTextExtractorSpec extends Specification with Mockito with NoTimeConversions {

  "TesseractOcrTextExtractor" should {

    "call tesseract to extract text" in new OcrScope {
      val result = textExtractor.extractText(image, language)

      result must be_==(text).await
    }

    "delete input and output temp files" in new OcrScope {
      val result = textExtractor.extractText(image, language)
      Await.ready(result, Duration.Inf)

      there was one(textExtractor.fileSystem).deleteFile(imageFile)
      there was one(textExtractor.fileSystem).deleteFile(outputFile)
    }
    
  }

  trait OcrScope extends Scope {

    val tesseract = "tesseract"
    val image = smartMock[BufferedImage]
    val language = "eng"
    val text = "Text from OCR"
    val tempFileBase = "/path/to/tmp/file"
    val imageFile = smartMock[File]
    val outputFile = new File(s"$tempFileBase.txt")

    val tesseractCommand = s"$tesseract $tempFileBase.png $tempFileBase -l $language"
    val textExtractor = new TestTesseractOcrTextExtractor

    class TestTesseractOcrTextExtractor extends TesseractOcrTextExtractor {
      override implicit protected val executionContext = scala.concurrent.ExecutionContext.global
      override protected val shellRunner = smartMock[ShellRunner]
      override protected val ocrTimeout = 1 millis
      override protected val tesseractLocation = tesseract
      override val fileSystem = smartMock[FileSystem]

      fileSystem.writeImage(image) returns imageFile
      fileSystem.readText(outputFile) returns text

      imageFile.getAbsolutePath returns s"$tempFileBase.png"

      shellRunner.run(tesseractCommand, ocrTimeout) returns Future.successful(text)
    
    }
  }
}
  
