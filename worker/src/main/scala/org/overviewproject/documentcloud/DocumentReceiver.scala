package org.overviewproject.documentcloud

import akka.actor.Actor
import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import scala.concurrent.Promise


/**
 * Actor that serializes the processing of documents retrieved from DocumentCloud.
 * Calls the specified callback function with the received document information and text.
 * When the specified number of documents has been received, the finished Promise
 * is completed.
 * If a document retrieval fails because of an exception or other error, the finished
 * promise will fail.
 * 
 * @todo Handle exceptions in callback
 * 
 * @param processDocument The callback function that does the actual processing of the documents.
 * @param numberOfDocuments The number of documents to retrieved
 * @param finished Contains information about any failed document retrieval attempts
 */
class DocumentReceiver(processDocument: (Document, String) => Unit, numberOfDocuments: Int, finished: Promise[Seq[DocumentRetrievalError]]) extends Actor {

  var receivedDocuments: Int = 0 
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
    case GetTextError(error) => finished.failure(error)
  }
  
  /** 
   *  Ensure that finished is completed. If some external source stops the actor, the Promise still succeeds
   */
  override def postStop = if (!finished.isCompleted) finished.success(failedRetrievals)
  
  private def update: Unit = {
    receivedDocuments += 1
    if (receivedDocuments == numberOfDocuments) finished.success(failedRetrievals)
  }
}