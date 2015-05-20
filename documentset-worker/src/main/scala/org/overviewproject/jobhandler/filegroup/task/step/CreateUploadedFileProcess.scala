package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.SlickClient
import org.overviewproject.jobhandler.filegroup.task.UploadedFileProcessCreator
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions
import akka.actor.ActorRef
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.database.SlickSessionProvider
import scala.util.Try
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess

trait CreateUploadedFileProcess extends UploadedFileProcessStep with SlickClient {
  override protected val documentSetId: Long

  protected val uploadedFile: GroupedFileUpload
  protected val options: UploadProcessOptions
  protected val documentIdSupplier: ActorRef

  override protected lazy val filename: String = uploadedFile.name

  protected val uploadedFileProcessCreator: UploadedFileProcessCreator

  override protected def doExecute: Future[TaskStep] = for {
    process <- createProcess
    firstStep <- process.start(uploadedFile)
  } yield firstStep

  private def createProcess: Future[UploadedFileProcess] = AsFuture {
    uploadedFileProcessCreator.create(uploadedFile, options, documentSetId, documentIdSupplier)
  }
}

object CreateUploadedFileProcess {
  def apply(documentSetId: Long, uploadedFile: GroupedFileUpload,
            options: UploadProcessOptions, documentIdSupplier: ActorRef): CreateUploadedFileProcess =
    new CreateUploadedFileProcessImpl(documentSetId, uploadedFile, options, documentIdSupplier)

  private class CreateUploadedFileProcessImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFile: GroupedFileUpload,
    override protected val options: UploadProcessOptions,
    override protected val documentIdSupplier: ActorRef) extends CreateUploadedFileProcess with SlickSessionProvider {

    override protected val uploadedFileProcessCreator = UploadedFileProcessCreator()
  }

}