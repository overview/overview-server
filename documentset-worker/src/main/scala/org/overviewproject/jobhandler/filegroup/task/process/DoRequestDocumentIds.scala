package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.PdfFileDocumentData
import org.overviewproject.models.Document
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.jobhandler.filegroup.task.step.RequestDocumentIds
import akka.actor.ActorRef

class DoRequestDocumentIds(documentIdSupplier: ActorRef, documentSetId: Long)
  extends StepGenerator[Seq[PdfFileDocumentData], Seq[Document]] {
  override def generate(d: Seq[PdfFileDocumentData]): TaskStep = {
    RequestDocumentIds(documentIdSupplier, documentSetId, d, nextStepGenerator.get.generate _)
  }
}