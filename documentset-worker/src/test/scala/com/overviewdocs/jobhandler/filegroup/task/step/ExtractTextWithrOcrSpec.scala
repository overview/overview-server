package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.collection.SeqView
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.overviewdocs.jobhandler.filegroup.task.PdfDocument
import com.overviewdocs.models.File
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import com.overviewdocs.jobhandler.filegroup.task.PdfPage

class ExtractTextWithrOcrSpec extends Specification with Mockito {

  "ExtractTextWithOcr" should {

    "start ocr step if needed" in new PdfContext {
      val r = extractTextStep.execute

      r must be_==(OcrStep(pdfFile, document)).await
    }
  }

  trait PdfContext extends Scope {
    val fileName = "file name"
    val viewLocation = "view location"
    val fileId = 1l
    val pdfFile = smartMock[File]
    pdfFile.name returns fileName
    pdfFile.viewLocation returns viewLocation
    pdfFile.id returns fileId

    val document = smartMock[PdfDocument]

    val extractTextStep = new TestExtractTextWithOcr(document)

    class TestExtractTextWithOcr(document: PdfDocument) extends ExtractTextWithOcr {
      override protected val executor: ExecutionContext = implicitly
      override protected val documentSetId = 1l
      override protected val file = pdfFile

      override protected val pdfProcessor = smartMock[PdfProcessor]
      pdfProcessor.loadFromBlobStorage(viewLocation) returns Future.successful(document)

      override protected def startOcr(f: File, d: PdfDocument): TaskStep = OcrStep(f, d)
    }

  }


  case class OcrStep(file: File, pdfDocument: PdfDocument) extends TaskStep {
    override def execute = Future.successful(this)
  }

}