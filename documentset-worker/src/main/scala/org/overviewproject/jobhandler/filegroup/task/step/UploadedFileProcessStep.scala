package org.overviewproject.jobhandler.filegroup.task.step

trait UploadedFileProcessStep extends TaskStep {
  protected val documentSetId: Long
  protected val filename: String
  
  override protected def errorHandler(t: Throwable): Unit = 
    WriteDocumentProcessingError(documentSetId, filename, t.getMessage)
}