package org.overviewproject.http

import scala.concurrent.duration._

import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions

import com.ning.http.client.Response

import akka.testkit.TestActorRef


class RequestQueueSpec extends Specification with Mockito with NoTimeConversions { // avoid conflicts with akka time conversion
  sequential

  trait ClientSetup extends Scope {
    val MaxInFlightRequests = 5
    val client = new TestClient
    val request = PublicRequest("url")
    val response = mock[Response]
    val timeout = 5 seconds
    
    response.getStatusCode returns 200
    response.getResponseBody returns "body"

  }


  trait ShortTimeout extends ClientSetup {
    override val timeout = 10 millis
  }
  
  abstract class ClientContext extends ActorSystemContext with ClientSetup {
    def createRequestQueue(maxInFlightRequests: Int = MaxInFlightRequests): TestActorRef[RequestQueue] = 
      TestActorRef(new RequestQueue(client, maxInFlightRequests, timeout))
  }

  abstract class ShortTimeoutClientContext extends ActorSystemContext with ShortTimeout {
    def createRequestQueue(maxInFlightRequests: Int = MaxInFlightRequests): TestActorRef[RequestQueue] = 
      TestActorRef(new RequestQueue(client, maxInFlightRequests, timeout))
  }

  
  "RequestQueue" should {

    "handle one request" in new ClientContext {
      val requestQueue = createRequestQueue()
      requestQueue ! AddToEnd(request)

      client.completeAllRequests(response)
      expectMsgType[Result](1 seconds)
    }

    "only have N requests in flight at a time" in new ClientContext {
      val requestQueue = createRequestQueue()
      
      1 to (MaxInFlightRequests + 1) foreach {_ => requestQueue ! AddToEnd(request) }

      client.requestsInFlight must be equalTo(MaxInFlightRequests)
      
      client.completeNext(response)
      client.requestsInFlight must be equalTo(MaxInFlightRequests)
      
      client.completeAllRequests(response)

      client.requestsInFlight must be equalTo(0)
    }

    "add requests to the front of the queue" in new ClientContext {
      val frontUrl = "front url"
      val requestQueue = createRequestQueue(maxInFlightRequests = 1)
      
      1 to 5 foreach {_ => requestQueue ! AddToEnd(request) }
      
      requestQueue ! AddToFront(PublicRequest(frontUrl))
      
      client.completeNext(response)
      client.requestedUrls.headOption must beSome(frontUrl)
    }
    
    "treat failed requests as completed" in new ClientContext {
       val failure = new Exception("failed")
       val requestQueue = createRequestQueue(maxInFlightRequests = 1)
       
       requestQueue ! AddToEnd(request)
       requestQueue ! AddToEnd(request)
       
       client.failNext(failure)
       
       expectMsg(Failure(failure))
       
       client.requestsInFlight must be equalTo(1)
    }

    "shutdown client when stopping" in new ClientContext {
      val requestQueue = createRequestQueue()
      
      requestQueue.stop()
      awaitCond(requestQueue.isTerminated)
      
      client.isShutdown must beTrue
    }
    
    "do something if request super timeouts" in new ShortTimeoutClientContext {
      val requestQueue = createRequestQueue()
      
      requestQueue ! AddToEnd(request)
      
      awaitCond(client.requestFuture.isCancelled)
    }
    
    "do not try to cancel completed requesrs" in new ShortTimeoutClientContext {
       val requestQueue = createRequestQueue()
       
       requestQueue ! AddToEnd(request)
       client.completeNext(response)
       Thread.sleep(100) // Is there a better way to check that isCancelled stays false?
       client.requestFuture.isCancelled must beFalse
    }
  }
}
