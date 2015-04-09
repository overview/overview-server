package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

trait UploadedFileProcess {

  def start(uploadedFileId: Long): TaskStep = steps.generate(uploadedFileId)
  
  protected val steps: StepGenerator[Long, _]
}