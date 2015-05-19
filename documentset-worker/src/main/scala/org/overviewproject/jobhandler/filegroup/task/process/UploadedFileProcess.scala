package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.GroupedFileUpload
import scala.concurrent.Future

trait UploadedFileProcess { 

  def start(uploadedFile: GroupedFileUpload): Future[TaskStep] = steps.generate(uploadedFile).execute
  
  protected val steps: StepGenerator[GroupedFileUpload, _]
}