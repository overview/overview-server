package org.overviewproject.documentcloud

import akka.actor._
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol.AddToEnd

class DocumentRetriever(requestQueue: ActorRef) extends Actor {
  private def DocumentQuery(d: Document): String = s"https://www.documentcloud.org/api/documents/${d.id}.txt"
  
  def receive = {
    case d: Document => requestQueue ! AddToEnd(PublicRequest(DocumentQuery(d)))
  }

}