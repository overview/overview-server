package org.overviewproject.documentcloud

import akka.actor.Actor
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.GetTextSucceeded
import scala.concurrent.Promise

class DocumentReceiver(processDocument: (Document, String) => Unit, numberOfDocuments: Int, finished: Promise[Int]) extends Actor {

  var receivedDocuments: Int = 0 // argh. Easiest way to keep track for now

  def receive = {
    case GetTextSucceeded(document, text) => {
      processDocument(document, text)
      receivedDocuments += 1
      if (receivedDocuments == numberOfDocuments) finished.success(receivedDocuments)
    }
  }
}