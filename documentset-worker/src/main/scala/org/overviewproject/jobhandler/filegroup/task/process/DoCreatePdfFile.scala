package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext

import org.overviewproject.jobhandler.filegroup.task.step.CreatePdfFile
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File
import org.overviewproject.models.GroupedFileUpload

object DoCreatePdfFile {
  def apply(documentSetId: Long, name: String)(implicit executor: ExecutionContext) =
    new StepGenerator[GroupedFileUpload, File] {
      override def generate(uploadedFile: GroupedFileUpload): TaskStep =
        CreatePdfFile(documentSetId, name, uploadedFile, nextStepFn)
    }
}