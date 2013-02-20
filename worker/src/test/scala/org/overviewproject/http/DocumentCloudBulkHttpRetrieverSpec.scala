package org.overviewproject.http

import akka.actor.ActorSystem
import com.ning.http.client.Response
import java.util.concurrent.TimeoutException
import org.specs2.mutable.After
import org.specs2.mutable.Specification
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.overviewproject.clustering.{ DCDocumentAtURL, PrivateDCDocumentAtURL }

class DocumentCloudBulkHttpRetrieverSpec extends Specification {

  "DocumentCloudBulkHttpRetriever" should {

    trait HttpResponseContext extends SuccessfulRetriever with After {
      self: RetrieverProvider =>

      val redirectingHttpRetriever = new TestHttpRetriever(actorSystem) {
    	var redirectsProcessed: Int = 0

        override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
          onFailure: Throwable => Unit) {

          redirectsProcessed += 1
          val response = new TestResponse
          onSuccess(response)
        }
      }
      
      var documentsRetrieved: Int = 0
      
      def processDocument(document: DCDocumentAtURL, result: String): Boolean = {
        documentsRetrieved += 1
        true         
      }
      
      def after = actorSystem.shutdown
    }

    "Request public documents directly" in new HttpResponseContext {
      val urlsToRetrieve = Seq.fill(5)(new DCDocumentAtURL("title", "id", "url")) ++ 
      Seq.fill(5)(new PrivateDCDocumentAtURL("title", "id", "url", "username", "password"))
      
      val bulkRetriever = new DocumentCloudBulkHttpRetriever(retriever, redirectingHttpRetriever)
      
      val failedDocs = bulkRetriever.retrieve(urlsToRetrieve, processDocument)
      Await.result(failedDocs.future, Duration("500ms")) must not(throwA[TimeoutException])

      documentsRetrieved must be equalTo 10
      redirectingHttpRetriever.redirectsProcessed must be equalTo 5
    }

  }

}
