package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.step.CreateDocumentData
import org.overviewproject.jobhandler.filegroup.task.step.DocumentData
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File

object DoCreateDocumentData {
  def apply(documentSetId: Long)(implicit executor: ExecutionContext) =
    new StepGenerator[(File, PdfDocument, Seq[String]), Seq[DocumentData]] {
    
    override def generate(documentInfo: (File, PdfDocument, Seq[String])): TaskStep =
      CreateDocumentData(documentSetId, nextStepFn,  documentInfo._1, documentInfo._2, documentInfo._3)
  }
}