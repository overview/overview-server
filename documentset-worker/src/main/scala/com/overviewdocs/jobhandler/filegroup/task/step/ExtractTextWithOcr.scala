package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.collection.SeqView
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.jobhandler.filegroup.task.{PdfBoxDocument,PdfDocument}
import com.overviewdocs.models.File

case class ExtractTextWithOcr(
  override val documentSetId: Long,
  file: File,
  nextStep: ((File, Seq[(String,Boolean)])) => TaskStep,
  language: String
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected val filename = file.name

  override protected def doExecute: Future[TaskStep] = {
    Future.successful(OcrDocumentPages(documentSetId, file, language, nextStep))
  }
}
