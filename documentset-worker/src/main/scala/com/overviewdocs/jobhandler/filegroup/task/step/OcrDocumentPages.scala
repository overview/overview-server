package com.overviewdocs.jobhandler.filegroup.task.step

import scala.collection.SeqView
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.jobhandler.filegroup.task.{PdfBoxDocument,PdfDocument,PdfPage}
import com.overviewdocs.models.File

case class OcrDocumentPages(
  override val documentSetId: Long,
  file: File,
  language: String,
  nextStep: ((File, Seq[String])) => TaskStep,
  ocrTextExtractor: OcrTextExtractor = TesseractOcrTextExtractor
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep { self =>
  override protected val filename = file.name

  case class OcrNextPage(
    pdfDocument: PdfDocument,
    remainingPages: Iterable[PdfPage],
    textBeforeRemainingPages: Seq[String]
  ) extends UploadedFileProcessStep {
    override val documentSetId = self.documentSetId
    override val filename = self.filename
    override implicit val executor = self.executor

    override protected def doExecute: Future[TaskStep] = {
      remainingPages.headOption match {
        case Some(page) => ocrAndClosePage(page)
        case None => {
          pdfDocument.close
          Future.successful(nextStep((file, textBeforeRemainingPages)))
        }
      }
    }

    private def ocrAndClosePage(page: PdfPage): Future[TaskStep] = {
      findPageText(page).map { text =>
        page.close
        OcrNextPage(pdfDocument, remainingPages.tail, textBeforeRemainingPages :+ text)
      }
    }

    private def findPageText(page: PdfPage): Future[String] = {
      page.textWithFonts match {
        case Right(text) if text.size >= OcrDocumentPages.MinimumTextSize => Future.successful(text)
        case _ => ocrPage(page)
      }
    }

    private def ocrPage(page: PdfPage): Future[String] = ocrTextExtractor.extractText(page.image, language)
  }

  protected def loadPdfDocumentFromBlobStorage(location: String): Future[PdfDocument] = {
    PdfBoxDocument.loadFromLocation(location)
  }

  override protected def doExecute: Future[TaskStep] = {
    for {
      pdfDocument <- loadPdfDocumentFromBlobStorage(file.contentsLocation)
    } yield OcrNextPage(pdfDocument, pdfDocument.pages, Seq())
  }
}

object OcrDocumentPages {
  val MinimumTextSize: Int = 100
}
