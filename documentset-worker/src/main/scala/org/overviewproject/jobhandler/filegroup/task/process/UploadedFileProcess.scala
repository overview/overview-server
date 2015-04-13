package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.GroupedFileUpload

trait UploadedFileProcess {

  def start(uploadedFile: GroupedFileUpload): TaskStep = steps.generate(uploadedFile)
  
  protected val steps: StepGenerator[GroupedFileUpload, _]
}