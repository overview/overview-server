package com.overviewdocs.documentcloud

import akka.actor._
import scala.concurrent.Future
import scala.util.{Failure,Success}

import com.overviewdocs.http.{Client=>HttpClient,Credentials,Response=>HttpResponse}
import com.overviewdocs.util.Logger

object DocumentRetrieverProtocol {
  /** Start retrieving the document */
  case class Start()
  
  sealed trait CompletionMessage

  /** Retrieval request succeeded, with resulting text.
    *
    * The text may contain control characters, null characters, and other
    * invalid stuff.
    **/
  case class GetTextSucceeded(d: Document, rawText: String) extends CompletionMessage
  /** Retrieval request completed with non-200 status code, or failed. */
  case class GetTextFailed(url: String, message: String, statusCode: Option[Int], headers: Option[Map[String,Seq[String]]]) extends CompletionMessage

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
  * @param httpClient sends the HTTP request
  * @param credentials will be used to authenticate the request, if present
  */
class DocumentRetriever(
  document: Document,
  recipient: ActorRef,
  httpClient: HttpClient,
  credentials: Option[Credentials]
) extends Actor {
  private val logger = Logger.forClass(getClass)

  import context.dispatcher
  import DocumentRetrieverProtocol._

  private val PublicAccess: String = "public"
  private val LocationHeader: String = "Location"
  private val OkStatus: Int = 200
  private val RedirectStatus: Int = 302

  private var retryAttempt: Int = 0

  def receive = {
    case Start() => requestDocument
  }

  def requestDocument: Unit = {
    sendHttpRequest.andThen {
      case Success(r) if r.statusCode == OkStatus => {
        forwardResult(new String(r.bodyBytes, "utf-8")) // force encoding: #85536256
      }
      case Success(r) => forwardNotOkResult(r)
      case Failure(t) => forwardException(t)
    }
  }

  private def sendHttpRequest: Future[HttpResponse] = {
    document.access match {
      case PublicAccess => httpClient.get(document.url)
      case _ => httpClient.get(document.url, credentials.get, false).flatMap(_ match {
        // We expect DocumentCloud to redirect us to an S3 URL, which does not
        // take authentication. If DocumentCloud *doesn't* redirect us, then
        // the response isn't what we want, and we'll handle that later.
        case r: HttpResponse if r.statusCode == RedirectStatus && r.headers.getOrElse(LocationHeader, Seq()).nonEmpty => {
          httpClient.get(r.headers(LocationHeader).head) // The S3 URL is public -- no auth
        }
        case r => Future.successful(r)
      })
    }
  }

  private def forwardResult(rawText: String): Unit = completeRetrieval(GetTextSucceeded(document, rawText))

  private def forwardNotOkResult(r: HttpResponse): Unit = {
    logger.warn(
      "Unable to retrieve document from {}. access: {}, status: {}, headers: {}, body: {}",
      document.url,
      document.access,
      r.statusCode,
      r.headers.toString,
      new String(r.bodyBytes, "utf-8")
    )
    completeRetrieval(GetTextFailed(
      document.url,
      new String(r.bodyBytes, "utf-8"),
      Some(r.statusCode),
      Some(r.headers)
    ))
  }

  private def forwardException(t: Throwable): Unit = {
    logger.warn(
      "Exception retrieving document from {}. access: {}, message: {}",
      document.url,
      document.access,
      t.getMessage
    )
    completeRetrieval(GetTextFailed(
      document.url,
      t.getMessage,
      None,
      None
    ))
  }

  private def completeRetrieval(message: CompletionMessage): Unit = {
    recipient ! message
    context.parent ! JobComplete()
    
    context.stop(self)
  }
}
