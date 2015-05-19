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

trait CreateUploadedFileProcess extends UploadedFileProcessStep with SlickClient {
  override protected val documentSetId: Long

  protected val uploadedFileId: Long
  protected val options: UploadProcessOptions
  protected val documentIdSupplier: ActorRef

  override protected lazy val filename: String = s"Uploaded File id: $uploadedFileId"

  protected val uploadedFileProcessCreator: UploadedFileProcessCreator

  override protected def doExecute: Future[TaskStep] = for {
    uploadedFile <- findUploadedFile
    process = uploadedFileProcessCreator.create(uploadedFile, options, documentSetId, documentIdSupplier)
    firstStep <- process.start(uploadedFile)
  } yield firstStep

  private def findUploadedFile: Future[GroupedFileUpload] = db { implicit session =>
    GroupedFileUploads.filter(_.id === uploadedFileId).first

  }
}

object CreateUploadedFileProcess {
  def apply(documentSetId: Long, uploadedFileId: Long,
            options: UploadProcessOptions, documentIdSupplier: ActorRef): CreateUploadedFileProcess =
    new CreateUploadedFileProcessImpl(documentSetId, uploadedFileId, options, documentIdSupplier)

  private class CreateUploadedFileProcessImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFileId: Long,
    override protected val options: UploadProcessOptions,
    override protected val documentIdSupplier: ActorRef) extends CreateUploadedFileProcess with SlickSessionProvider {
    
    override protected val uploadedFileProcessCreator = UploadedFileProcessCreator()
  }

}