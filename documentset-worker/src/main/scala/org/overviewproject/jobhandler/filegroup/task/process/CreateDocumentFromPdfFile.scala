package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

trait CreateDocumentFromPdfFile {
  def start(file: File): TaskStep
}

object CreateDocumentFromPdfFile {
  def apply(documentSetId: Long, documentIdSupplier: ActorRef) = new CreateDocumentFromPdfFile {
    private val steps =
      new DoExtractTextFromPdf(documentSetId).andThen(
        new DoRequestDocumentIds(documentIdSupplier, documentSetId).andThen(
          DoWriteDocuments()))
          
    override def start(file: File) = steps.generate(file)
  }
}