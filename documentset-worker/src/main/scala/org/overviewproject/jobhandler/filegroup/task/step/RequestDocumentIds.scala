package org.overviewproject.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.models.Document

case class DocumentIdRequest(numberOfIds: Long)

trait RequestDocumentIds extends TaskStep {
  protected val documentIdSupplier: ActorRef
  
  protected val documentSetId: Long
  protected val documentData: Seq[PdfFileDocumentData]

  protected def nextStep(documents: Seq[Document]): TaskStep

  protected def nextStepForResponse(documentSetIds: Seq[Long]): TaskStep = {
    val documents = for {
      (id, data) <- documentSetIds zip documentData

    } yield data.toDocument(documentSetId, id)

    nextStep(documents)
  }

  override def execute: Future[TaskStep] = Future {
    documentIdSupplier ! DocumentIdRequest(1)

    WaitForResponse(nextStepForResponse _)
  }
}

object RequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, documentSetId: Long, documentData: Seq[PdfFileDocumentData],
      nextStep: Seq[Document] => TaskStep)
  : RequestDocumentIds = new RequestDocumentIdsImpl(documentIdSupplier, documentSetId, documentData, nextStep)

  private class RequestDocumentIdsImpl(
    override protected val documentIdSupplier: ActorRef,
    override protected val documentSetId: Long,
    override protected val documentData: Seq[PdfFileDocumentData],
    next: Seq[Document] => TaskStep) extends RequestDocumentIds {

    override protected def nextStep(documents: Seq[Document]): TaskStep = next(documents)
  }
}