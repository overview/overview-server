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

class TesseractOcrTextExtractorSpec extends Specification with Mockito with NoTimeConversions {

  "TesseractOcrTextExtractor" should {
    
    "call tesseract to extract text" in new OcrScope {
      val result = textExtractor.extractText(image, language)
      
      result must be_==(text).await
    }
  }
  
  trait OcrScope extends Scope {
    
    val tesseract = "tesseract"
    val image = smartMock[BufferedImage]
    val language = "eng"
    val text = "Text from OCR"
    val tempFileBase = "/path/to/tmp/file"
    
    val tesseractCommand = s"$tesseract $tempFileBase.png $tempFileBase.txt -l $language"
    val textExtractor = new TestTesseractOcrTextExtractor
    
    class TestTesseractOcrTextExtractor extends TesseractOcrTextExtractor {
      override implicit protected val executionContext = scala.concurrent.ExecutionContext.global
      override protected val shellRunner = smartMock[ShellRunner]
      override protected val ocrTimeout = 1 millis
      override protected val tesseractLocation = tesseract
      override protected val fileSystem = smartMock[FileSystem]
      
      private val imageFile = smartMock[File]
      private val outputFile = new File(s"$tempFileBase.txt")

      fileSystem.writeImage(image) returns imageFile
      fileSystem.readText(outputFile) returns text
      
      imageFile.getAbsolutePath returns s"$tempFileBase.png"
      
      shellRunner.run(tesseractCommand, ocrTimeout) returns Future.successful(text)
      
      
    }
  }
}