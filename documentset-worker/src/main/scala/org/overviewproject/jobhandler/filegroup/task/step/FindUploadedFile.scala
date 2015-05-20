package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.GroupedFileUpload
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.models.tables.GroupedFileUploads

trait FindUploadedFile extends UploadedFileProcessStep with SlickClient {
  
  override protected val documentSetId: Long
  override protected lazy val filename: String = s"Uploaded file id: $uploadedFileId" 
  
  protected val uploadedFileId: Long
  
  protected val nextStep: GroupedFileUpload => TaskStep
  
  override protected def doExecute: Future[TaskStep] = for {
    uploadedFile <- findUploadedFile
  } yield nextStep(uploadedFile)
    
    private def findUploadedFile: Future[GroupedFileUpload] = db { implicit session =>
    
    GroupedFileUploads.filter(_.id === uploadedFileId).first
    
  }
}