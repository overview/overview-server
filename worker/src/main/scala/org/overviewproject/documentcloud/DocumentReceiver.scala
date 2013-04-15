package org.overviewproject.documentcloud

import akka.actor.Actor
import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import scala.concurrent.Promise


class DocumentReceiver(processDocument: (Document, String) => Unit, numberOfDocuments: Int, finished: Promise[Seq[DocumentRetrievalError]]) extends Actor {

  var receivedDocuments: Int = 0 // argh. Easiest way to keep track for now
  var failedRetrievals: Seq[DocumentRetrievalError] = Seq.empty
  
  def receive = {
    case GetTextSucceeded(document, text) => {
      processDocument(document, text)
      update
    }
    case GetTextFailed(url, text, maybeStatus, maybeHeaders) => {
      failedRetrievals = failedRetrievals :+ DocumentRetrievalError(url, text, maybeStatus, maybeHeaders)
      update
    }
  }
  
  override def postStop = if (!finished.isCompleted) finished.success(failedRetrievals)
  
  private def update: Unit = {
    receivedDocuments += 1
    if (receivedDocuments == numberOfDocuments) finished.success(failedRetrievals)
  }
}