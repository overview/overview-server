package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.database.FileGroupDeleter

case class DeleteFileUploadComplete(documentSetId: Long, fileGroupId: Long) extends FileGroupTaskStep {
  override def execute = this
}

/**
 * Deletes the document set and file group.
 * 
 * FIXME: [[execute]] should return a [[Future]], then we wouldn't have to explicitly block.
 */
class DeleteFileUploadTaskStep(documentSetId: Long, fileGroupId: Long,
                               documentSetDeleter: DocumentSetDeleter, fileGroupDeleter: FileGroupDeleter)
  extends FileGroupTaskStep {

  override def execute: FileGroupTaskStep = {
    val documentSetDeletion = documentSetDeleter.delete(documentSetId)
    val fileGroupDeletion = fileGroupDeleter.delete(fileGroupId)

    val r = for {
      d <- documentSetDeletion
      f <- fileGroupDeletion
    } yield DeleteFileUploadComplete(documentSetId, fileGroupId)

    Await.result(r, Duration.Inf)
  }
}