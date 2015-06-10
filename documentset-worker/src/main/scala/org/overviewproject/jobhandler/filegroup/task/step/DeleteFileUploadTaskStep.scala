package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.database.FileGroupDeleter
import org.overviewproject.database.DocumentSetCreationJobDeleter
import org.overviewproject.database.TempFileDeleter


/**
 * Deletes the document set and file group.
 *
 * FIXME: [[execute]] should return a [[Future]], then we wouldn't have to explicitly block.
 */
trait DeleteFileUploadTaskStep extends TaskStep {
  protected val jobDeleter: DocumentSetCreationJobDeleter
  protected val documentSetDeleter: DocumentSetDeleter
  protected val fileGroupDeleter: FileGroupDeleter
  protected val tempFileDeleter: TempFileDeleter

  protected val documentSetId: Long
  protected val fileGroupId: Long

  protected def nextStep: TaskStep
  
  override protected def doExecute: Future[TaskStep] =
    deleteJobThenCleanup.map { _ => nextStep }

  private def deleteJobThenCleanup: Future[Unit] =
    for {
      job <- jobDeleter.deleteByDocumentSet(documentSetId)
      tempFiles <- tempFileDeleter.delete(documentSetId) 
      upload <- deleteUploadRemains     
    } yield ()


  private def deleteUploadRemains: Future[Unit] = {
    val documentSetDeletion = documentSetDeleter.delete(documentSetId)
    val fileGroupDeletion = fileGroupDeleter.delete(fileGroupId)

    for {
      documentSet <- documentSetDeletion
      fileGroup <- fileGroupDeletion
    } yield ()

  }

}

object DeleteFileUploadTaskStep {
  def apply(documentSetId: Long, fileGroupId: Long, nextStep: TaskStep): DeleteFileUploadTaskStep =
    new DeleteFileUploadTaskStepImpl(documentSetId, fileGroupId, nextStep)

  private class DeleteFileUploadTaskStepImpl(
    override protected val documentSetId: Long,
    override protected val fileGroupId: Long,
    override protected val nextStep: TaskStep) extends DeleteFileUploadTaskStep {

    override protected val jobDeleter = DocumentSetCreationJobDeleter
    override protected val documentSetDeleter = DocumentSetDeleter
    override protected val fileGroupDeleter = FileGroupDeleter
    override protected val tempFileDeleter = TempFileDeleter

  }
}
