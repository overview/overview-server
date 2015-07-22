package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext

import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import org.overviewproject.jobhandler.filegroup.task.step.ExtractTextWithOcr
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File

object DoExtractTextWithOcr {

  def apply(documentSetId: Long, language: String, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext) = 
    new StepGenerator[File, (File, PdfDocument, Seq[String])] {
    override def generate(file: File): TaskStep =
      ExtractTextWithOcr(documentSetId, file, nextStepFn, language, timeoutGenerator)
  }
}