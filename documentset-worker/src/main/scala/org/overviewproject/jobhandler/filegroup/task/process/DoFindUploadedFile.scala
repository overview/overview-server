package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.FindUploadedFile
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.GroupedFileUpload

object DoFindUploadedFile {

  def apply(documentSetId: Long) = new StepGenerator[Long, GroupedFileUpload] {
    
    override def generate(uploadedFileId: Long): TaskStep = 
      FindUploadedFile(documentSetId, uploadedFileId, nextStepFn)
  }
}