package org.overviewproject.documentcloud

import akka.actor._
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol.AddToEnd
import org.overviewproject.http.Credentials
import org.overviewproject.http.PrivateRequest

class DocumentRetriever(requestQueue: ActorRef, credentials: Option[Credentials]) extends Actor {
  
  private val PublicAccess: String = "public"
  private def DocumentQuery(d: Document): String = s"https://www.documentcloud.org/api/documents/${d.id}.txt"
  
  def receive = {
    case d: Document if isPublic(d) => requestQueue ! AddToEnd(PublicRequest(DocumentQuery(d)))
    case d: Document => credentials.map { c => requestQueue ! AddToEnd(PrivateRequest(DocumentQuery(d), c)) }
  }

  private def isPublic(d: Document): Boolean = d.access == PublicAccess
}