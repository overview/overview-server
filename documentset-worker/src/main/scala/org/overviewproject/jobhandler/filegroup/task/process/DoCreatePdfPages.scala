package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.CreatePdfPages
import org.overviewproject.jobhandler.filegroup.task.step.DocumentData
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File

object DoCreatePdfPages {

  def apply(documentSetId: Long) = new StepGenerator[File, Seq[DocumentData]] {
    
    override def generate(file: File): TaskStep = 
      CreatePdfPages(documentSetId, file, nextStepFn)
    
  }
}