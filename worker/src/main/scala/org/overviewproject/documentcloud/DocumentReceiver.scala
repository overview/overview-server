package org.overviewproject.documentcloud

import akka.actor.Actor

import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import scala.concurrent.Promise
import scala.util.control.Exception._


object DocumentReceiverProtocol {
  /** No more documents will be processed */
  case class Done(documentsRetrieved: Int, totalDocumentsInQuery: Int)
}

/** Information about documents that could not be retrieved */
case class DocumentRetrievalError(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)



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
 * @param textify Callback to convert "raw text" to text. See org.overviewproject.util.Textify
 * @param processDocument The callback function that does the actual processing of the documents.
 * @param finished To be completed with information about the document retrievals
 */
class DocumentReceiver(textify: (String) => String, processDocument: (Document, String) => Unit, finished: Promise[RetrievalResult]) extends Actor {
  import DocumentReceiverProtocol._
  
  var receivedDocuments: Int = 0 
  var failedRetrievals: Seq[DocumentRetrievalError] = Seq.empty
  
  private case class Result(failedRetrievals: Seq[DocumentRetrievalError], numberOfDocumentsRetrieved: Int, totalDocumentsInQuery: Int) extends RetrievalResult
  
  def receive = {
    case GetTextSucceeded(document, rawText) => {
      failOnError { processDocument(document, textify(rawText)) }
    }
    case GetTextFailed(url, text, maybeStatus, maybeHeaders) => {
      failedRetrievals = failedRetrievals :+ DocumentRetrievalError(url, text, maybeStatus, maybeHeaders)
    }
    case GetTextError(error) => finished.failure(error)
    case Done(documentsRetrieved, totalDocumentsInQuery) => finished.success(Result(failedRetrievals, documentsRetrieved, totalDocumentsInQuery))
  }
  
  /** 
   *  Ensure that finished is completed. If some external source stops the actor, the Promise still succeeds
   */
  override def postStop = if (!finished.isCompleted) finished.success(Result(failedRetrievals, 0, 0))

  /** Apply to methods that can throw, and complete `finished` with failure if they do */
  private val failOnError = allCatch withApply { error => finished.failure(error) }
  
}
