package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext

import com.overviewdocs.jobhandler.filegroup.task.step.CreatePdfFile
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.models.File
import com.overviewdocs.models.GroupedFileUpload

object DoCreatePdfFile {
  def apply(documentSetId: Long, name: String, lang: String)(implicit executor: ExecutionContext) =
    new StepGenerator[GroupedFileUpload, File] {
      override def generate(uploadedFile: GroupedFileUpload): TaskStep =
        CreatePdfFile(documentSetId, name, uploadedFile, lang, nextStepFn)
    }
}
