/*
 * DocumentCloudBulkHttpRetriever.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, January 2013
 */
package org.overviewproject.http

import akka.actor.ActorRef
import scala.concurrent.Promise
import org.overviewproject.clustering.{ DCDocumentAtURL, PrivateDCDocumentAtURL }
import com.ning.http.client.Response
import org.overviewproject.util.Logger

/**
 * Manages a queue of http requests retrieving documents from DocumentCloud. If documents are private,
 * uses the redirectingHttpRetriever to find the private document url and submit a new
 * request for the actual document.
 * The separate redirectingHttpRetriever is needed because AsyncHttpClient forwards Basic Auth
 * headers in response to a redirect. The call to retrieve the document from amazon S3 then fails
 * because it rejects the authentication.
 */
class DocumentCloudBulkHttpRetriever(asyncHttpRetriever: AsyncHttpRetriever,
  nonRedirectingHttpRetriever: AsyncHttpRetriever) extends BulkHttpRetriever[DCDocumentAtURL](asyncHttpRetriever) {

  private case class RetrievePrivateDocUrl(pDoc: PrivateDCDocumentAtURL)
  private case class PrivateDocToRetrieve(doc: DCDocumentAtURL)

  /**
   * Actor that handles the extra step to retrieve private documents
   */
  protected class DocumentCloudRetrieverActor(writeDocument: (DCDocumentAtURL, String) => Boolean,
    finished: Promise[Seq[DocRetrievalError]]) extends BulkHttpActor[DCDocumentAtURL](writeDocument, finished) {

    /** Use the nonRedirectingHttpRetriever for private documents */
    override protected def requestDocument(request: Request, startTime: Long) = request.doc match {
      case pd: PrivateDCDocumentAtURL =>
        nonRedirectingHttpRetriever.request(pd, request.handler(pd, startTime, _), requestFailed(pd, _))
      case _ => super.requestDocument(request, startTime)
    }

    /** Add handlers for messages specific to private documents */
    override def receive = receivePrivateDocumentMsg orElse super.receive


    /** Messages for dealing with private documents */
    private def receivePrivateDocumentMsg: PartialFunction[Any, Unit] = {
      // Create a request for the private document's url
      case RetrievePrivateDocUrl(pDoc) => 
        Logger.info("Requesting private doc URL for " + pDoc.textURL )
        requestQueue += Request(pDoc, requestForPrivateDocUrlSucceeded)
        spoolRequests
      // Once a private doc is retrieved, decrease httpReqInFlight to reflect
      // that the _earlier_ request for the url is complete.
      // Decrementing here ensures that the retriever doesn't stop prematurely
      case PrivateDocToRetrieve(doc) =>
        httpReqInFlight -= 1
        requestQueue += Request(doc, requestSucceeded)
        spoolRequests
    }

    // When a private document's url is retrieved, generate a new
    // request for the actual document
    private def requestForPrivateDocUrlSucceeded(doc: DocumentAtURL, startTime: Long, result: Response) = {
      val privateDocInfo = doc.asInstanceOf[PrivateDCDocumentAtURL] // FIXME

      val privateUrl = result.getHeader("Location")
      val privateDoc = new DCDocumentAtURL(privateDocInfo.title, privateDocInfo.documentCloudId, privateUrl)
      
      self ! PrivateDocToRetrieve(privateDoc)
    }
  }

  /** Create an actor that can handle documentcloud private documents */
  override protected def createActor(writeDocument: (DCDocumentAtURL, String) => Boolean, retrievalDone: Promise[Seq[DocRetrievalError]]) =
    new DocumentCloudRetrieverActor(writeDocument, retrievalDone)

  /** If a document is private, request its url */
  override protected def retrieveDocument(doc: DCDocumentAtURL, retriever: ActorRef) = doc match {
    case pd: PrivateDCDocumentAtURL => retriever ! RetrievePrivateDocUrl(pd)
    case _ => super.retrieveDocument(doc, retriever)
  }
}
