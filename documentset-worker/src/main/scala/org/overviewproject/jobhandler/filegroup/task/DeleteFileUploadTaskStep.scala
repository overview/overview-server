package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.database.FileGroupDeleter
import org.overviewproject.database.DocumentSetCreationJobDeleter

case class DeleteFileUploadComplete(documentSetId: Long, fileGroupId: Long) extends FileGroupTaskStep {
  override def execute = this
}

/**
 * Deletes the document set and file group.
 *
 * FIXME: [[execute]] should return a [[Future]], then we wouldn't have to explicitly block.
 */
class DeleteFileUploadTaskStep(documentSetId: Long, fileGroupId: Long,
                               jobDeleter: DocumentSetCreationJobDeleter,
                               documentSetDeleter: DocumentSetDeleter,
                               fileGroupDeleter: FileGroupDeleter)
  extends FileGroupTaskStep {

  override def execute: FileGroupTaskStep =
    Await.result(
      deleteJobThenCleanup,
      Duration.Inf)

  private def deleteJobThenCleanup: Future[FileGroupTaskStep] =
    for {
      first <- jobDeleter.deleteByDocumentSet(documentSetId)
      next <- deleteUploadRemains
    } yield next

  private def deleteUploadRemains: Future[FileGroupTaskStep] = {
    val documentSetDeletion = documentSetDeleter.delete(documentSetId)
    val fileGroupDeletion = fileGroupDeleter.delete(fileGroupId)

    for {
      d <- documentSetDeletion
      f <- fileGroupDeletion
    } yield DeleteFileUploadComplete(documentSetId, fileGroupId)

  }

}