package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.DocumentData
import org.overviewproject.jobhandler.filegroup.task.step.RequestDocumentIds
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.Document

import akka.actor.ActorRef

object DoRequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, documentSetId: Long) =
    new StepGenerator[Seq[DocumentData], Seq[Document]] {
      override def generate(d: Seq[DocumentData]): TaskStep = {
        RequestDocumentIds(documentIdSupplier, documentSetId, d, nextStepFn)
      }
    }
}