package org.overviewproject.documentcloud

import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol._
import akka.actor._
import org.overviewproject.http.SimpleResponse
import com.ning.http.client.FluentCaseInsensitiveStringsMap

object DocumentRetrieverProtocol {
  case class Start()
  case class GetTextSucceeded(d: Document, text: String)
  case class GetTextFailed(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)
}

class DocumentRetriever(document: Document, recipient: ActorRef, requestQueue: ActorRef, credentials: Option[Credentials]) extends Actor {
  import DocumentRetrieverProtocol._
  
  private val PublicAccess: String = "public"
  private val LocationHeader: String = "Location"
  private val OkStatus: Int = 200
  private val RedirectStatus: Int = 302
  
  private def DocumentQuery(d: Document): String = s"https://www.documentcloud.org/api/documents/${d.id}.txt"
  
  def receive = {
    case Start() => requestDocument
    case Result(r) if isOk(r) => forwardResult(r.body)
    case Result(r) if isRedirect(r) => redirectRequest(r)
    case Result(r) => failRequest(r)
  }

  def requestDocument: Unit = {
    if (isPublic(document)) makePublicRequest(DocumentQuery(document))
    else makePrivateRequest(DocumentQuery(document))
  }
  
  private def isPublic(d: Document): Boolean = d.access == PublicAccess
  private def isRedirect(r: SimpleResponse): Boolean = r.status == RedirectStatus
  private def isOk(r: SimpleResponse): Boolean = r.status == OkStatus
  
  private def requestPublicDocument(d: Document): Unit = makePublicRequest(DocumentQuery(d))
  private def requestPrivateDocument(d: Document): Unit = credentials.map { c => requestQueue ! AddToFront(PrivateRequest(DocumentQuery(d), c)) }
  
  private def forwardResult(text: String): Unit = {
    recipient ! GetTextSucceeded(document, text)
    context.stop(self)
  }
  
  private def failRequest(r: SimpleResponse): Unit = {
    recipient ! GetTextFailed(DocumentQuery(document), r.body, Some(r.status), Some(r.headersToString))
    context.stop(self)
  }
  
  private def makePublicRequest(url: String): Unit = requestQueue ! AddToEnd(PublicRequest(url))
  private def makePrivateRequest(url: String): Unit = credentials.map { c => requestQueue ! AddToFront(PrivateRequest(url, c)) }
  
  private def redirectRequest(response: SimpleResponse): Unit = response.headers(LocationHeader).map { url => makePublicRequest(url) }
}