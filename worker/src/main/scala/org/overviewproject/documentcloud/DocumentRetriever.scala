package org.overviewproject.documentcloud

import org.overviewproject.http.{ Credentials, PrivateRequest, PublicRequest }
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.http.SimpleResponse
import akka.actor._
import org.overviewproject.util.Logger

object DocumentRetrieverProtocol {
  /** Start retrieving the document */
  case class Start()
  
  trait CompletionMessage
  /** Retrieval request succeeded, with resulting text */
  case class GetTextSucceeded(d: Document, text: String) extends CompletionMessage
  /** Retrieval request completed but failed with the specified message */
  case class GetTextFailed(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None) extends CompletionMessage
  /** An error occurred when trying to retrieve the document */
  case class GetTextError(t: Throwable) extends CompletionMessage
  
  /** Notify parent that job has completed */
  case class JobComplete()
}

/**
 * Actor that tries to retrieve one document and forwards the result.
 * If the document is private, a request will be submitted to the front
 * of the `requestQueue`. DocumentCloud will respond with a redirect, containing the
 * actual location of the document (including an authentication token). When the location is received,
 * the request is submitted without authentication credentials.
 *
 * After the result has been forwarded, the DocumentRetriever actor will stop itself.
 *
 * @todo Retry when request fails
 * @todo Add private document request to the front of the queue, to avoid auth token from expiring.
 *
 * @param document contains the information needed to request the document from DocumentCloud
 * @param recipient is the final destination for the retrieved document text
 * @param requestQueue handles the retrieval requests
 * @param credentials will be used to authenticate the request, if present
 * @param requestRetryTimes Determines retry behavior
 */
class DocumentRetriever(document: Document, recipient: ActorRef, requestQueue: ActorRef, credentials: Option[Credentials], retryTimes: RequestRetryTimes) extends Actor {
  import DocumentRetrieverProtocol._

  private val PublicAccess: String = "public"
  private val LocationHeader: String = "Location"
  private val OkStatus: Int = 200
  private val RedirectStatus: Int = 302 // FIXME: Should we check other redirect response codes?

  private var retryAttempt: Int = 0

  def receive = {
    case Start() => requestDocument
    case Result(r) if isOk(r) => forwardResult(r.body)
    case Result(r) if isRedirect(r) => redirectRequest(r)
    case Result(r) => retryOr(failRequest(r))
    case Failure(t: Exception) => retryOr(convertToFailure(t))
    case Failure(t) => forwardError(t)
  }

  def requestDocument: Unit = {
    val query = document.url

    if (isPublic(document)) makePublicRequest(query)
    else makePrivateRequest(query)
  }

  private def isPublic(d: Document): Boolean = (d.access == PublicAccess)
  private def isRedirect(r: SimpleResponse): Boolean = r.status == RedirectStatus
  private def isOk(r: SimpleResponse): Boolean = r.status == OkStatus

  private def forwardResult(text: String): Unit = completeRetrieval(GetTextSucceeded(document, text))

  private def failRequest(r: SimpleResponse): Unit = {
    Logger.warn(s"Unable to retrieve document from ${document.url} ) access: ${document.access} status: ${r.status}\n${r.headersToString}\n${r.body}")
    completeRetrieval(GetTextFailed(document.url, r.body, Some(r.status), Some(r.headersToString)))
  }

  private def forwardError(t: Throwable): Unit = {
    Logger.error(s"Unable to process ${document.url}: ${t.getMessage()}")
    completeRetrieval(GetTextError(t))
  }

  /**
   * We hope that exceptions are specific to this particular request, so we
   * convert it to a failure so the receiver will not abort.
   */
  private def convertToFailure(t: Exception): Unit = completeRetrieval(GetTextFailed(document.url, t.toString))

  
  
  private def completeRetrieval(message: CompletionMessage): Unit = {
    recipient ! message
    context.parent ! JobComplete()
    
    context.stop(self)
  }
  

  private def makePublicRequest(url: String): Unit = 
    if (retryAttempt == 0) requestQueue ! AddToEnd(PublicRequest(url))
    else requestQueue ! AddToFront(PublicRequest(url))

  /**
   * Private requests are added to the front of the queue because we expect a redirect response
   * to be returned quickly.
   */
  private def makePrivateRequest(url: String): Unit = credentials.map { c => requestQueue ! AddToFront(PrivateRequest(url, c, redirect = false)) }

  private def redirectRequest(response: SimpleResponse): Unit =
    response.headers(LocationHeader).headOption map { url => makePublicRequest(url) }

  private def retryOr(giveUp: => Unit): Unit = {
    retryTimes(retryAttempt) match {
      case Some(delay) => {
        Logger.warn(s"Scheduling retry attempt $retryAttempt for ${document.url} access: ${document.access}")
        import context.dispatcher
        context.system.scheduler.scheduleOnce(delay, self, Start())
        retryAttempt += 1
      }
      case None => giveUp
    }
  }
}