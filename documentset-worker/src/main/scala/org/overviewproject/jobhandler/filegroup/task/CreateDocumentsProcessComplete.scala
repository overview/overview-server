package org.overviewproject.jobhandler.filegroup.task

case class CreateDocumentsProcessComplete(documentSetId: Long) extends FileGroupTaskStep {
  override def execute: FileGroupTaskStep = return this
}