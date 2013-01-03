/*
 * BulkHttpRetrieverSpec
 *
 * Overview Project
 * Created by Jonas Karlsson, September 2012
 */
package org.overviewproject.http

import akka.dispatch.Await
import akka.util.Timeout
import org.overviewproject.clustering.DCDocumentAtURL
import org.specs2.mutable.Specification
import org.specs2.specification.After


class BulkHttpRetrieverSpec extends Specification {

  "BulkHttpRetriever" should {

    /**
     * Context for counting number of processed requests to the AsyncHttpRetriever
     */
    trait SimulatedWebClient extends After {
      this: RetrieverProvider =>
      var requestsProcessed = new scala.collection.mutable.ArrayBuffer[String]
      val bulkHttpRetriever = new BulkHttpRetriever[DCDocumentAtURL](retriever)
      val urlsToRetrieve = Seq.fill(10)(new DCDocumentAtURL("title", "id", "url"))

      def processDocument(document: DCDocumentAtURL, result: String): Boolean = {
        requestsProcessed += result
        true
      }

      def after = actorSystem.shutdown
    }

    trait SuccessfulRequests extends SuccessfulRetriever with SimulatedWebClient
    trait FailingRequests extends FailingRetriever with SimulatedWebClient

    "retrieve and process documents" in new SuccessfulRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must beEmpty
      requestsProcessed must have size (urlsToRetrieve.size)
    }

    "collect failed requests" in new FailingRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must have size (urlsToRetrieve.size)
    }

  }

}
