package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.models.{DocumentDisplayMethod,File}

case class CreateDocumentData(
  override val documentSetId: Long,
  nextStep: Seq[DocumentWithoutIds] => TaskStep,
  file: File,
  textPages: Seq[(String,Boolean)]
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected val filename = file.name

  /** Text is the sum of all pages of text.
    */
  def text = textPages.map(_._1).mkString("\n")

  /** If any one page is OCR, we flag the file as OCRd in the database.
    */
  def isFromOcr = textPages.map(_._2).exists(x => x)

  override protected def doExecute: Future[TaskStep] = Future.successful { 
    val document = DocumentWithoutIds(
      url=None,
      suppliedId=filename,
      title=filename,
      pageNumber=None,
      keywords=Seq(),
      createdAt=new java.util.Date(),
      fileId=Some(file.id),
      pageId=None,
      displayMethod=DocumentDisplayMethod.auto,
      isFromOcr=isFromOcr,
      metadataJson=JsObject(Seq()),
      text=text
    )
    nextStep(Seq(document))
  }
}
