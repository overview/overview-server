package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.step.DocumentData
import org.overviewproject.jobhandler.filegroup.task.step.RequestDocumentIds
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.Document


object DoRequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, documentSetId: Long, filename: String)(implicit executor: ExecutionContext) =
    new StepGenerator[Seq[DocumentData], Seq[Document]] {
      override def generate(d: Seq[DocumentData]): TaskStep = {
        RequestDocumentIds(documentIdSupplier, documentSetId, filename, d, nextStepFn)
      }
    }
}