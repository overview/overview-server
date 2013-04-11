package org.overviewproject.documentcloud

import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol._
import akka.actor._
import org.overviewproject.http.SimpleResponse

class DocumentRetriever(requestQueue: ActorRef, credentials: Option[Credentials]) extends Actor {
  
  private val PublicAccess: String = "public"
  private val LocationHeader: String = "Location"
  private val RedirectStatus: Int = 302
  
  private def DocumentQuery(d: Document): String = s"https://www.documentcloud.org/api/documents/${d.id}.txt"
  
  def receive = {
    case d: Document if isPublic(d) => requestPublicDocument(d)
    case d: Document => requestPrivateDocument(d)
    case Result(r) if isRedirect(r) => redirectRequest(r) 
  }

  private def isPublic(d: Document): Boolean = d.access == PublicAccess
  private def isRedirect(r: SimpleResponse): Boolean = r.status == RedirectStatus
  
  private def requestPublicDocument(d: Document): Unit = requestQueue ! AddToEnd(PublicRequest(DocumentQuery(d)))
  private def requestPrivateDocument(d: Document): Unit = credentials.map { c => requestQueue ! AddToFront(PrivateRequest(DocumentQuery(d), c)) }
  
  private def redirectRequest(response: SimpleResponse): Unit = response.headers(LocationHeader).map { url =>
    requestQueue ! AddToEnd(PublicRequest(url))
  }
  
}