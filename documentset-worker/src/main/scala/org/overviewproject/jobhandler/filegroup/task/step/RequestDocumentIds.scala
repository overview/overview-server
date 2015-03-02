package org.overviewproject.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DocumentIdRequest(numberOfIds: Long)

trait RequestDocumentIds extends TaskStep {
   protected val documentIdSupplier: ActorRef
   protected val document: PdfFileDocumentData
   
   protected def nextStepForResponse(documentSetIds: Seq[Long]): TaskStep
   
   override def execute: Future[TaskStep] = Future {
     documentIdSupplier ! DocumentIdRequest(1)
     
     WaitForResponse(nextStepForResponse)
   }
}

object RequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, document: PdfFileDocumentData, nextStep: Seq[Long] => TaskStep):
  RequestDocumentIds = new RequestDocumentIdsImpl(documentIdSupplier, document, nextStep)
  
  private class RequestDocumentIdsImpl(
      override protected val documentIdSupplier: ActorRef,
      override protected val document: PdfFileDocumentData,
      nextStep: Seq[Long] => TaskStep) extends RequestDocumentIds {
    override protected def nextStepForResponse(documentSetIds: Seq[Long]): TaskStep = nextStep(documentSetIds)
  }
}