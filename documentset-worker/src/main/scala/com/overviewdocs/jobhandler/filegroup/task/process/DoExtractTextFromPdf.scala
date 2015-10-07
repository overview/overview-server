package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.jobhandler.filegroup.task.step.DocumentWithoutIds
import com.overviewdocs.jobhandler.filegroup.task.step.ExtractTextFromPdf
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.models.File

object DoExtractTextFromPdf {

  def apply(documentSetId: Long)(implicit executor: ExecutionContext) = new StepGenerator[File, Seq[DocumentWithoutIds]] {

    override def generate(f: File): TaskStep = {
      ExtractTextFromPdf(documentSetId, f, nextStepFn)
    }
  }
}
