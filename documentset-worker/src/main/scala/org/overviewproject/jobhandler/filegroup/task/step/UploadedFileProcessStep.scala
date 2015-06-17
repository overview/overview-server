package org.overviewproject.jobhandler.filegroup.task.step



/**
 * A [[TaskStep]] that writes a [[DocumentProcessingError]] if the step fails.
 */
trait UploadedFileProcessStep extends ErrorHandlingTaskStep {

  protected val documentSetId: Long
  protected val filename: String

  override protected def errorHandler(t: Throwable): Unit = {
    def message =
      if (t.getMessage == null) "null message"
      else t.getMessage
      
     WriteDocumentProcessingError.write(documentSetId, filename, message)      
  }
}
