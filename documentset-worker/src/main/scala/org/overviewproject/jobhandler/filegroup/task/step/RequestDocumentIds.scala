package org.overviewproject.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.models.Document

case class DocumentIdRequest(requestId: Long, documentSetId: Long, numberOfIds: Long)

trait RequestDocumentIds extends TaskStep {
  protected val documentIdSupplier: ActorRef
  
  protected val documentSetId: Long
  protected val documentData: Seq[DocumentData]

  protected val nextStep: Seq[Document] => TaskStep

  protected def nextStepForResponse(documentSetIds: Seq[Long]): TaskStep = {
    val documents = for {
      (id, data) <- documentSetIds zip documentData

    } yield data.toDocument(documentSetId, id)

    nextStep(documents)
  }

  override def execute: Future[TaskStep] = Future {
    documentIdSupplier ! DocumentIdRequest(1, documentSetId, 1)

    WaitForResponse(nextStepForResponse _)
  }
}

object RequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, documentSetId: Long, documentData: Seq[DocumentData],
      nextStep: Seq[Document] => TaskStep)
  : RequestDocumentIds = new RequestDocumentIdsImpl(documentIdSupplier, documentSetId, documentData, nextStep)

  private class RequestDocumentIdsImpl(
    override protected val documentIdSupplier: ActorRef,
    override protected val documentSetId: Long,
    override protected val documentData: Seq[DocumentData],
    override protected val nextStep: Seq[Document] => TaskStep) extends RequestDocumentIds 

}