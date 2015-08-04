package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext

import com.overviewdocs.jobhandler.filegroup.task.PdfDocument
import com.overviewdocs.jobhandler.filegroup.task.TimeoutGenerator
import com.overviewdocs.jobhandler.filegroup.task.step.ExtractTextWithOcr
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.models.File

object DoExtractTextWithOcr {

  def apply(documentSetId: Long, language: String, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext) = 
    new StepGenerator[File, (File, PdfDocument, Seq[String])] {
    override def generate(file: File): TaskStep =
      ExtractTextWithOcr(documentSetId, file, nextStepFn, language, timeoutGenerator)
  }
}