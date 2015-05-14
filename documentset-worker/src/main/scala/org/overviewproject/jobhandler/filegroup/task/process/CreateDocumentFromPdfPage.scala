package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

object CreateDocumentFromPdfPage {
  def apply(documentSetId: Long, documentIdSupplier: ActorRef) = new UploadedFileProcess {
    override protected val steps =
      DoCreatePdfFile(documentSetId).andThen(
        DoCreatePdfPages().andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId).andThen(
            DoWriteDocuments())))

  }
}