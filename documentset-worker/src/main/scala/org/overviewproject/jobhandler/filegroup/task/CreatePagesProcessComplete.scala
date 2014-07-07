package org.overviewproject.jobhandler.filegroup.task


/** 
 *  Signifies the end of the [[CreatePagesProcess]] 
 *  @param fileId is the id of the created file, or `None` if no file could be created
 */
case class CreatePagesProcessComplete(documentSetId: Long, uploadedFileId: Long, fileId: Option[Long]) extends FileGroupTaskStep {
  override def execute: FileGroupTaskStep = return CreatePagesProcessComplete.this
}
