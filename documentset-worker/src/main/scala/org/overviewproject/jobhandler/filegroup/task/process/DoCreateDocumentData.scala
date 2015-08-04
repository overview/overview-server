package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.jobhandler.filegroup.task.PdfDocument
import com.overviewdocs.jobhandler.filegroup.task.step.CreateDocumentData
import com.overviewdocs.jobhandler.filegroup.task.step.DocumentData
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.models.File

object DoCreateDocumentData {
  def apply(documentSetId: Long)(implicit executor: ExecutionContext) =
    new StepGenerator[(File, PdfDocument, Seq[String]), Seq[DocumentData]] {
    
    override def generate(documentInfo: (File, PdfDocument, Seq[String])): TaskStep =
      CreateDocumentData(documentSetId, nextStepFn,  documentInfo._1, documentInfo._2, documentInfo._3)
  }
}