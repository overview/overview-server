package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.step.DocumentData
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.jobhandler.filegroup.task.step.ExtractTextWithOcr
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator

object DoExtractTextWithOcr {

  def apply(documentSetId: Long, language: String, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext) = new StepGenerator[File, Seq[DocumentData]] {
    override def generate(file: File): TaskStep =
      ExtractTextWithOcr(documentSetId, file, nextStepFn, language, timeoutGenerator)
  }
}