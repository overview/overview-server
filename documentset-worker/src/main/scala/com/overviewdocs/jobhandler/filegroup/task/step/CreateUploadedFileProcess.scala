package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.ActorRef
import com.overviewdocs.jobhandler.filegroup.task.UploadProcessOptions
import com.overviewdocs.jobhandler.filegroup.task.UploadedFileProcessCreator
import com.overviewdocs.jobhandler.filegroup.task.process.UploadedFileProcess
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.util.BulkDocumentWriter
import com.overviewdocs.jobhandler.filegroup.task.TimeoutGenerator



/**
 * Create a process to convert a [[GroupedFileUpload]] into [[Document]](s).
 */
trait CreateUploadedFileProcess extends UploadedFileProcessStep {
  override protected val documentSetId: Long

  protected val uploadedFile: GroupedFileUpload
  protected val options: UploadProcessOptions
  protected val documentIdSupplier: ActorRef
  protected val bulkDocumentWriter: BulkDocumentWriter

  override protected lazy val filename: String = uploadedFile.name

  protected val uploadedFileProcessCreator: UploadedFileProcessCreator

  override protected def doExecute: Future[TaskStep] = for {
    process <- createProcess
    firstStep <- process.start(uploadedFile)
  } yield firstStep

  private def createProcess: Future[UploadedFileProcess] = AsFuture {
    uploadedFileProcessCreator.create(uploadedFile, options, documentSetId,
      documentIdSupplier, bulkDocumentWriter)
  }
}

object CreateUploadedFileProcess {
  def apply(documentSetId: Long, uploadedFile: GroupedFileUpload, options: UploadProcessOptions,
            timeoutGenerator: TimeoutGenerator, documentIdSupplier: ActorRef,
            bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext): CreateUploadedFileProcess =
    new CreateUploadedFileProcessImpl(documentSetId, uploadedFile, options,
      timeoutGenerator, documentIdSupplier, bulkDocumentWriter)

  private class CreateUploadedFileProcessImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFile: GroupedFileUpload,
    override protected val options: UploadProcessOptions,
    timeoutGenerator: TimeoutGenerator,
    override protected val documentIdSupplier: ActorRef,
    override protected val bulkDocumentWriter: BulkDocumentWriter
  )(override implicit protected val executor: ExecutionContext) extends CreateUploadedFileProcess {
    override protected val uploadedFileProcessCreator = 
      UploadedFileProcessCreator(bulkDocumentWriter, timeoutGenerator)
  }

}
