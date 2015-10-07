package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import com.overviewdocs.jobhandler.filegroup.DocumentIdSupplierProtocol._
import com.overviewdocs.models.Document
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout


/**
 * Send a request to the documentIdSupplier actor, asking for [[Document]] ids.
 * 
 * The documentIdSupplier must respond within the [[timeout]] or the request will fail.
 */
trait RequestDocumentIds extends UploadedFileProcessStep {
  protected implicit val timeout = Timeout(5 seconds) // timeout for Ask request
  protected val documentIdSupplier: ActorRef

  override protected val documentSetId: Long
  override protected val filename: String
  
  protected val documentData: Seq[DocumentWithoutIds]

  protected val nextStep: Seq[Document] => TaskStep

  override protected def doExecute: Future[TaskStep] = for {
    IdRequestResponse(ids) <- documentIdSupplier ? RequestIds(documentSetId, documentData.size)
  } yield nextStep(createDocuments(ids)) 

  private def createDocuments(documentSetIds: Seq[Long]): Seq[Document] =
    for {
      (id, data) <- documentSetIds zip documentData
    } yield data.toDocument(documentSetId, id)

}

object RequestDocumentIds {
  def apply(documentIdSupplier: ActorRef, documentSetId: Long, filename: String, documentData: Seq[DocumentWithoutIds],
            nextStep: Seq[Document] => TaskStep)(implicit executor: ExecutionContext): RequestDocumentIds = 
              new RequestDocumentIdsImpl(documentIdSupplier, documentSetId, filename, documentData, nextStep)

  private class RequestDocumentIdsImpl(
    override protected val documentIdSupplier: ActorRef,
    override protected val documentSetId: Long,
    override protected val filename: String,
    override protected val documentData: Seq[DocumentWithoutIds],
    override protected val nextStep: Seq[Document] => TaskStep)
   (override implicit protected val executor: ExecutionContext) extends RequestDocumentIds

}
