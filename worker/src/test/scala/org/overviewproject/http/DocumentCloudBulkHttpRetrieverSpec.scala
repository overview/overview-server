package org.overviewproject.http

import org.specs2.mutable.Specification
import org.specs2.mutable.After
import akka.actor.ActorSystem
import akka.dispatch.ExecutionContext
import com.ning.http.client.Response

class DocumentCloudBulkHttpRetrieverSpec extends Specification {
  
  "DocumentCloudBulkHttpRetriever" should {
    
    trait HttpResponseContext extends After {
      implicit val actorSystem = ActorSystem("TestActorSystem") 
      
      def after = actorSystem.shutdown
    }
    
    "Request public documents directly" in new HttpResponseContext {
      //val retriever = new DocumentCloudBulkHttpRetriever(asyncHttpRetriever, redirectingHttpRetriever)
      
      
      
    }
  
  }

}