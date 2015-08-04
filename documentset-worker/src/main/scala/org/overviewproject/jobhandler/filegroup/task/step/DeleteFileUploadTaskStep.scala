package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.database.FileGroupDeleter
import com.overviewdocs.database.DocumentSetCreationJobDeleter
import com.overviewdocs.database.TempFileDeleter

/**
 * Deletes the document set and file group.
 *
 * FIXME: [[execute]] should return a [[Future]], then we wouldn't have to explicitly block.
 */
trait DeleteFileUploadTaskStep extends ErrorHandlingTaskStep {
  protected val jobDeleter: DocumentSetCreationJobDeleter
  protected val documentSetDeleter: DocumentSetDeleter
  protected val fileGroupDeleter: FileGroupDeleter
  protected val tempFileDeleter: TempFileDeleter

  protected val documentSetId: Long
  protected val fileGroupId: Long

  protected def nextStep: TaskStep

  override protected def doExecute: Future[TaskStep] =
    deleteJobThenCleanup.map { _ => nextStep }

  private def deleteJobThenCleanup: Future[Unit] = {
    for {
      _ <- jobDeleter.deleteByDocumentSet(documentSetId)
      _ <- tempFileDeleter.delete(documentSetId)
      _ <- documentSetDeleter.delete(documentSetId)
      _ <- fileGroupDeleter.delete(fileGroupId)
    } yield ()
  }
}

object DeleteFileUploadTaskStep {
  def apply(documentSetId: Long, fileGroupId: Long,
            nextStep: TaskStep)(implicit executor: ExecutionContext): DeleteFileUploadTaskStep =
    new DeleteFileUploadTaskStepImpl(documentSetId, fileGroupId, nextStep)

  private class DeleteFileUploadTaskStepImpl(
    override protected val documentSetId: Long,
    override protected val fileGroupId: Long,
    override protected val nextStep: TaskStep)(override implicit protected val executor: ExecutionContext) extends DeleteFileUploadTaskStep {

    override protected val jobDeleter = DocumentSetCreationJobDeleter
    override protected val documentSetDeleter = DocumentSetDeleter
    override protected val fileGroupDeleter = FileGroupDeleter
    override protected val tempFileDeleter = TempFileDeleter

  }
}
