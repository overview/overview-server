package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.models.{DocumentDisplayMethod,File}

case class CreateDocumentData(
  override val documentSetId: Long,
  isFromOcr: Boolean,
  nextStep: Seq[DocumentWithoutIds] => TaskStep,
  file: File,
  textPages: Seq[String]
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected val filename = file.name

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
      text=textPages.mkString("")
    )
    nextStep(Seq(document))
  }
}
