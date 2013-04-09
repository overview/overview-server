package org.overviewproject.http

import scala.concurrent.duration.DurationInt

import org.overviewproject.http.RequestQueueProtocol.{AddToEnd, Result}
import org.specs2.mock.Mockito
import org.specs2.mutable.{After, Specification}
import org.specs2.time.NoTimeConversions

import com.ning.http.client.Response

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}

class RequestQueueSpec extends Specification with Mockito with NoTimeConversions { // avoid conflicts with akka time conversion

  abstract class ActorSystemContext extends TestKit(ActorSystem()) with ImplicitSender with After {
    def after = system.shutdown()
  }
  
  "RequestQueue" should {
    
    "handle one request" in new ActorSystemContext {
      val response = mock[Response]
      response.getStatusCode returns 200
      response.getResponseBody returns "body"
      
      val client = new TestClient
      val requestQueue = TestActorRef(new RequestQueue(client)) // Single threaded testing
      
      requestQueue.tell(AddToEnd("url"), testActor)
      
      client.completeAllRequests(response)
      expectMsgType[Result](1 seconds)
    }
  }
}
