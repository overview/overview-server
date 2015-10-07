package com.overviewdocs.jobhandler.filegroup.task.process

import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.models.GroupedFileUpload
import scala.concurrent.Future

trait UploadedFileProcess { 
  def start(uploadedFile: GroupedFileUpload): Future[TaskStep] = steps.generate(uploadedFile).execute
  
  protected val steps: StepGenerator[GroupedFileUpload, _]
}
