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
import org.overviewproject.test.DbSpecification
import org.specs2.specification.After
import com.ning.http.client.Response

class BulkHttpRetrieverSpec extends DbSpecification {

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

    trait BadStatus extends RetrieverProvider {
      val status: Int = 403
      val retriever = new TestHttpRetriever(actorSystem) {
        override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
          onFailure: Throwable => Unit) {
          val response = new TestResponse(403, "Forbidden")
          onSuccess(response)
        }
      }
    }

    trait SuccessfulRequests extends SuccessfulRetriever with SimulatedWebClient
    trait FailingRequests extends FailingRetriever with SimulatedWebClient
    trait BadStatusRequests extends BadStatus with SimulatedWebClient

    "retrieve and process documents" in new SuccessfulRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must beEmpty
      requestsProcessed must have size (urlsToRetrieve.size)
    }

    "collect failed requests" in new FailingRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val expectedError = DocRetrievalError(urlsToRetrieve.head.textURL, "failed request")

      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must have size urlsToRetrieve.size

      requestsWithErrors.distinct must contain(expectedError).only
    }

    "collect requests with bad status" in new BadStatusRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val errorMessage = """|header1:value1
                            |header2:value2
                            |
                            |body""".stripMargin
                            
      val expectedError = DocRetrievalError(urlsToRetrieve.head.textURL, errorMessage, Some(status))
      
      val requestsWithErrors = Await.result(done, Timeout.never.duration)
      
      requestsWithErrors must have size urlsToRetrieve.size
      requestsWithErrors.distinct must contain(expectedError).only
    }

  }

}
