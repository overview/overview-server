package com.overviewdocs.http

import akka.testkit.TestActorRef
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.{Future,Promise}

import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.http.RequestQueueProtocol._

class RequestQueueSpec extends Specification with Mockito {
  sequential

  trait ClientSetup extends Scope {
    val MaxInFlightRequests = 2
    val client = smartMock[Client]
    val request = Request("url")
    val response = Response(200, Map(), "body".getBytes)

    client.get(any)(any) returns Future.successful(response)
  }
  
  abstract class ClientContext extends ActorSystemContext with ClientSetup {
    def createRequestQueue(maxInFlightRequests: Int = MaxInFlightRequests): TestActorRef[RequestQueue] = 
      TestActorRef(new RequestQueue(client, maxInFlightRequests))
  }
  
  "RequestQueue" should {

    "handle one request" in new ClientContext {
      val requestQueue = createRequestQueue()
      requestQueue ! AddToEnd(request)
      expectMsgType[HttpSuccess]
    }

    "only have N requests in flight at a time" in new ClientContext {
      val requestQueue = createRequestQueue(2)

      val promise = Promise[Response]()
      client.get(any)(any) returns promise.future // yeah, hack -- the same future

      (1 to 3).foreach { _ => requestQueue ! AddToEnd(request) }

      there were two(client).get(any)(any)
      promise.success(response)
      there were three(client).get(any)(any)
    }
    
    //"add requests to the front of the queue" in new ClientContext {
    //  val frontUrl = "front url"
    //  val requestQueue = createRequestQueue(maxInFlightRequests = 1)
    //  
    //  1 to 5 foreach {_ => requestQueue ! AddToEnd(request) }
    //  
    //  requestQueue ! AddToFront(Request(frontUrl))
    //  
    //  client.completeNext(response)
    //  client.requestedUrls.headOption must beSome(frontUrl)
    //}
    
    "treat failed requests as completed" in new ClientContext {
       val failure = new Exception("failed")
       val requestQueue = createRequestQueue(maxInFlightRequests = 1)

       client.get(any)(any) returns Future.failed(failure)
       
       requestQueue ! AddToEnd(request)
       requestQueue ! AddToEnd(request)

       expectMsg(HttpFailure(failure))
    }

    "shutdown client when stopping" in new ClientContext {
      val requestQueue = createRequestQueue()
      
      requestQueue.stop()

      there was one(client).shutdown
    }
  }
}
