package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.jobhandler.filegroup.task.step.CreatePdfFile

object DoCreatePdfFile {
   def  apply(documentSetId: Long) = new StepGenerator[Long, File] {
     
     override def generate(uploadedFileId: Long): TaskStep = 
       CreatePdfFile(documentSetId, uploadedFileId, nextStepFn)
   }
}