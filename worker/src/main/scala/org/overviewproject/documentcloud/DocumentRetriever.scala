package org.overviewproject.documentcloud

import akka.actor._
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol.AddToEnd

class DocumentRetriever(requestQueue: ActorRef) extends Actor {
  def receive = {
    case d: Document => requestQueue ! AddToEnd(PublicRequest("url"))
  }
}