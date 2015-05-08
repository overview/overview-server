package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import org.overviewproject.jobhandler.filegroup.DocumentIdSupplierProtocol._
import org.overviewproject.models.Document

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

trait RequestDocumentIds extends TaskStep {
  protected implicit val timeout = Timeout(5 seconds)
  protected val documentIdSupplier: ActorRef

  protected val documentSetId: Long
  protected val documentData: Seq[DocumentData]

  protected val nextStep: Seq[Document] => TaskStep

  override def execute: Future[TaskStep] = for {
    IdRequestResponse(ids) <- documentIdSupplier ? RequestIds(documentSetId, documentData.size)
  } yield nextStep(createDocuments(ids))

  private def createDocuments(documentSetIds: Seq[Long]): Seq[Document] =
    for {
      (id, data) <- documentSetIds zip documentData
    } yield data.toDocument(documentSetId, id)

}

object RequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, documentSetId: Long, documentData: Seq[DocumentData],
            nextStep: Seq[Document] => TaskStep): RequestDocumentIds = new RequestDocumentIdsImpl(documentIdSupplier, documentSetId, documentData, nextStep)

  private class RequestDocumentIdsImpl(
    override protected val documentIdSupplier: ActorRef,
    override protected val documentSetId: Long,
    override protected val documentData: Seq[DocumentData],
    override protected val nextStep: Seq[Document] => TaskStep) extends RequestDocumentIds

}