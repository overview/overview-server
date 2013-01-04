package org.overviewproject.http

import akka.actor.ActorRef
import akka.dispatch.Promise
import org.overviewproject.clustering.{ DCDocumentAtURL, PrivateDCDocumentAtURL }
import com.ning.http.client.Response
import overview.util.Logger

class DocumentCloudBulkHttpRetriever(asyncHttpRetriever: AsyncHttpRetriever,
  redirectingHttpRetriever: AsyncHttpRetriever) extends BulkHttpRetriever[DCDocumentAtURL](asyncHttpRetriever) {

  private case class PrivateDocToRetrieve(pDoc: PrivateDCDocumentAtURL)
  private case class PrivateDocToRetrieve2(doc: DCDocumentAtURL)

  protected class DocumentCloudRetrieverActor(writeDocument: (DCDocumentAtURL, String) => Boolean,
    finished: Promise[Seq[DocRetrievalError]]) extends BulkHttpActor[DCDocumentAtURL](writeDocument, finished) {

    override protected def requestDocument(request: Request, startTime: Long) = request.doc match {
      case pd: PrivateDCDocumentAtURL =>
        redirectingHttpRetriever.request(pd, request.handler(pd, startTime, _), requestFailed(pd, _))
      case _ => super.requestDocument(request, startTime)
    }

    override def receive = receivePrivateDocumentMsg orElse super.receive

    override protected def allRequestsProcessed: Boolean = super.allRequestsProcessed

    private def receivePrivateDocumentMsg: PartialFunction[Any, Unit] = {
      case PrivateDocToRetrieve(pDoc) =>
        Logger.debug("Requesting private doc URL for " + pDoc.textURL )
        requestQueue += Request(pDoc, requestForPrivateDocUrlSucceeded)
        spoolRequests
      case PrivateDocToRetrieve2(doc) =>
        httpReqInFlight -= 1
        Logger.debug("Number of private doc requests left: " + httpReqInFlight)
        requestQueue += Request(doc, requestSucceeded)
        spoolRequests
    }

    private def requestForPrivateDocUrlSucceeded(doc: DocumentAtURL, startTime: Long, result: Response) = {
      val privateDocInfo = doc.asInstanceOf[PrivateDCDocumentAtURL]

      val privateUrl = result.getHeader("Location")
      val privateDoc = new DCDocumentAtURL(privateDocInfo.title, privateDocInfo.documentCloudId, privateUrl)
      
      Logger.debug("Requesting private doc " + httpReqInFlight)
      self ! PrivateDocToRetrieve2(privateDoc)
    }
  }

  override protected def createActor(writeDocument: (DCDocumentAtURL, String) => Boolean, retrievalDone: Promise[Seq[DocRetrievalError]]) =
    new DocumentCloudRetrieverActor(writeDocument, retrievalDone)

  override protected def retrieveDocument(doc: DCDocumentAtURL, retriever: ActorRef) = doc match {
    case pd: PrivateDCDocumentAtURL => retriever ! PrivateDocToRetrieve(pd)
    case _ => super.retrieveDocument(doc, retriever)
  }
}