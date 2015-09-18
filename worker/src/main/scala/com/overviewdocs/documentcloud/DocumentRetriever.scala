package com.overviewdocs.documentcloud

import akka.actor._

import com.overviewdocs.http.{Credentials, Request, Response}
import com.overviewdocs.http.RequestQueueProtocol._
import com.overviewdocs.util.Logger

object DocumentRetrieverProtocol {
  /** Start retrieving the document */
  case class Start()
  
  trait CompletionMessage

  /** Retrieval request succeeded, with a 200 status code.
    *
    * The text may contain control characters, null characters, and other
    * invalid stuff.
    **/
  case class GetTextSucceeded(document: Document, body: String) extends CompletionMessage

  /** Retrieval request completed, but the status code was not 200. */
  case class GetTextBadResponse(url: String, response: Response) extends CompletionMessage

  /** Retrieval request failed. */
  case class GetTextException(url: String, ex: Throwable) extends CompletionMessage
  
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
 * @param document contains the information needed to request the document from DocumentCloud
 * @param recipient is the final destination for the retrieved document text
 * @param requestQueue handles the retrieval requests
 * @param credentials will be used to authenticate the request, if present
 */
class DocumentRetriever(
  document: Document,
  recipient: ActorRef,
  requestQueue: ActorRef,
  credentials: Option[Credentials]
) extends Actor {
  private val logger = Logger.forClass(getClass)

  import DocumentRetrieverProtocol._

  private val PublicAccess: String = "public"
  private val LocationHeader: String = "Location"
  private val OkStatus: Int = 200
  private val RedirectStatus: Int = 302

  def receive = {
    case Start() => requestDocument
    case HttpSuccess(response) => onHttpResponse(response)
    case HttpFailure(ex) => onHttpFailure(ex)
  }

  def requestDocument: Unit = {
    val query = document.url

    if (document.access == PublicAccess) {
      makePublicRequest(query)
    } else {
      makePrivateRequest(query)
    }
  }

  private def onHttpResponse(response: Response): Unit = response.statusCode match {
    case RedirectStatus => response.headers.getOrElse(LocationHeader, Seq()).foreach(makePublicRequest)
    case OkStatus => complete(GetTextSucceeded(document, response.body))
    case _ => {
      logger.warn(
        "Received non-200 status code from {}. access: {}, status: {}, headers: {}, body: {}",
        document.url,
        document.access,
        response.statusCode,
        response.headers.toString,
        response.body
      )
      complete(GetTextBadResponse(document.url, response))
    }
  }

  private def onHttpFailure(ex: Throwable): Unit = {
    logger.warn("Exception when requesting {}: {}", document.url, ex.toString)
    complete(GetTextException(document.url, ex))
  }

  private def complete(message: CompletionMessage): Unit = {
    recipient ! message
    context.parent ! JobComplete()
    context.stop(self)
  }

  /** Make a request that does not include credentials.
    *
    * We add this to the front of the queue, as we want private requests to
    * be quickly followed by public ones, so S3 auth tokens don't expire.
    */
  private def makePublicRequest(url: String): Unit = requestQueue ! AddToFront(Request(url))

  /** Make a request that includes credentials.
    *
    * DocumentCloud will return a Redirect response to a public (and temporary)
    * S3 URL. We'll want to request that URL before it expires; hence public
    * requests go to the front of the queue and private requests go to the
    * back.
    */
  private def makePrivateRequest(url: String): Unit = requestQueue ! AddToEnd(Request(url, credentials, false))
}
