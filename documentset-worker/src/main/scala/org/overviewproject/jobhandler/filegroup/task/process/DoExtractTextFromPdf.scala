package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.DocumentData
import org.overviewproject.jobhandler.filegroup.task.step.ExtractTextFromPdf
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File

object DoExtractTextFromPdf {

  def apply(documentSetId: Long) = new StepGenerator[File, Seq[DocumentData]] {

    override def generate(f: File): TaskStep = {
      ExtractTextFromPdf(documentSetId, f, nextStepFn)
    }
  }
}