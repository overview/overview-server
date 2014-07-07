package org.overviewproject.jobhandler.filegroup.task


/** Signifies the end of the [[CreatePagesProcess]] */
case class CreatePagesProcessComplete(documentSetId: Long, uploadedFileId: Long) extends FileGroupTaskStep {
  override def execute: FileGroupTaskStep = return CreatePagesProcessComplete.this
}
