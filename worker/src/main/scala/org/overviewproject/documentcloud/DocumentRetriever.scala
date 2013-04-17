package org.overviewproject.documentcloud

import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.http.SimpleResponse

import akka.actor._

object DocumentRetrieverProtocol {
  /** Start retrieving the document */
  case class Start()
  /** Retrieval request succeeded, with resulting text */
  case class GetTextSucceeded(d: Document, text: String)
  /** Retrieval request completed but failed with the specified message */
  case class GetTextFailed(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)
  /** An error occurred wen trying to retrieve the document */
  case class GetTextError(t: Throwable)
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
 */
class DocumentRetriever(document: Document, recipient: ActorRef, requestQueue: ActorRef, credentials: Option[Credentials]) extends Actor {
  import DocumentRetrieverProtocol._
  
  private val PublicAccess: String = "public"
  private val LocationHeader: String = "Location"
  private val OkStatus: Int = 200
  private val RedirectStatus: Int = 302 // FIXME: Should we check other redirect response codes?


  private def DocumentQuery(d: Document): String = s"https://www.documentcloud.org/api/documents/${d.id}.txt"

  def receive = {
    case Start() => requestDocument
    case Result(r) if isOk(r) => forwardResult(r.body)
    case Result(r) if isRedirect(r) => redirectRequest(r)
    case Result(r) => failRequest(r)
    case Failure(t) => forwardError(t)
  }

  def requestDocument: Unit = { 
    val query = DocumentQuery(document)
    
    if (isPublic(document)) makePublicRequest(query)
    else makePrivateRequest(query)
  }

  private def isPublic(d: Document): Boolean = d.access == PublicAccess
  private def isRedirect(r: SimpleResponse): Boolean = r.status == RedirectStatus
  private def isOk(r: SimpleResponse): Boolean = r.status == OkStatus

  private def forwardResult(text: String): Unit = { 
    recipient ! GetTextSucceeded(document, text)
    context.stop(self)
  }

  private def failRequest(r: SimpleResponse): Unit = {
    recipient ! GetTextFailed(DocumentQuery(document), r.body, Some(r.status), Some(r.headersToString))
    context.stop(self)
  }

  private def forwardError(t: Throwable): Unit = {
    recipient ! GetTextError(t)
    context.stop(self)
  }
  
  private def makePublicRequest(url: String): Unit = requestQueue ! AddToEnd(PublicRequest(url))
  
  /** 
   * Private requests are added to the front of the queue because we expect a redirect response
   * to be returned quickly. 
   */
  private def makePrivateRequest(url: String): Unit = credentials.map { c => requestQueue ! AddToFront(PrivateRequest(url, c, redirect = false)) }

  private def redirectRequest(response: SimpleResponse): Unit = 
    response.headers(LocationHeader).headOption map { url => makePublicRequest(url) } 
}