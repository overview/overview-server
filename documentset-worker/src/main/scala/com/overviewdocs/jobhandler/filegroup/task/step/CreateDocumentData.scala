package com.overviewdocs.jobhandler.filegroup.task.step

import org.overviewproject.pdfocr.pdf.PdfDocument
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{DocumentDisplayMethod,File}
import com.overviewdocs.util.Textify

class CreateDocumentData(
  override val documentSetId: Long,
  file: File,
  nextStep: Seq[DocumentWithoutIds] => TaskStep
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected val filename: String = file.name

  private def getText: Future[(String,Boolean)] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { file =>
      PdfDocument.load(file.toPath).flatMap { pdfDocument =>
        val pageTexts = new Array[String](pdfDocument.nPages)
        var isFromOcr = false
        val it = pdfDocument.pages
        def fillRemainingPageTexts: Future[Unit] = if (it.hasNext) {
          it.next.flatMap { pdfPage =>
            pageTexts(pdfPage.pageNumber) = pdfPage.toText
            if (!isFromOcr && pdfPage.isFromOcr) isFromOcr = true
            fillRemainingPageTexts
          }
        } else {
          Future.successful(())
        }

        fillRemainingPageTexts
          .andThen { case _ => pdfDocument.close }
          .map(_ => (Textify(pageTexts.mkString("\n")), isFromOcr))
      }
    }
  }

  override protected def doExecute: Future[TaskStep] = {
    for {
      (text, isFromOcr) <- getText
    } yield {
      val document = DocumentWithoutIds(
        url=None,
        suppliedId=file.name,
        title=file.name,
        pageNumber=None,
        keywords=Seq(),
        createdAt=new java.util.Date(),
        fileId=Some(file.id),
        pageId=None,
        displayMethod=DocumentDisplayMethod.pdf,
        isFromOcr=isFromOcr,
        metadataJson=JsObject(Seq()),
        text=text
      )

      nextStep(Seq(document))
    }
  }
}
