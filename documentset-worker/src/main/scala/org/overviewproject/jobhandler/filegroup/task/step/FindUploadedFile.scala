package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.models.tables.GroupedFileUploads


/**
 * Find the [[GroupedFileUpload]] with the specified id.
 */
trait FindUploadedFile extends UploadedFileProcessStep with HasDatabase {
  import database.api._

  override protected val documentSetId: Long
  override protected lazy val filename: String = s"Uploaded file id: $uploadedFileId"

  protected val uploadedFileId: Long

  protected val nextStep: GroupedFileUpload => TaskStep

  override protected def doExecute: Future[TaskStep] = for {
    uploadedFile <- findUploadedFile
  } yield nextStep(uploadedFile)

  private def findUploadedFile: Future[GroupedFileUpload] = {
    database.option(GroupedFileUploads.filter(_.id === uploadedFileId))
      .map(_.get)
  }
}

object FindUploadedFile {

  def apply(documentSetId: Long, uploadedFileId: Long,
            nextStep: GroupedFileUpload => TaskStep)(implicit executor: ExecutionContext): FindUploadedFile =
    new FindUploadedFileImpl(documentSetId, uploadedFileId, nextStep)

  private class FindUploadedFileImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFileId: Long,
    override protected val nextStep: GroupedFileUpload => TaskStep)
   (override implicit protected val executor: ExecutionContext) extends FindUploadedFile
}
