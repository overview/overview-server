package org.overviewproject.http

import scala.concurrent.duration.DurationInt
import org.overviewproject.http.RequestQueueProtocol.{ AddToEnd, Result }
import org.specs2.mock.Mockito
import org.specs2.mutable.{ After, Specification }
import org.specs2.time.NoTimeConversions
import com.ning.http.client.Response
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import org.specs2.specification.Scope

class RequestQueueSpec extends Specification with Mockito with NoTimeConversions { // avoid conflicts with akka time conversion
  sequential

  trait ClientContext extends Scope {
    val MaxInFlightRequests = 5
    val client = new TestClient
    val response = mock[Response]

    response.getStatusCode returns 200
    response.getResponseBody returns "body"

  }

  abstract class ActorSystemContext extends TestKit(ActorSystem()) with ImplicitSender with ClientContext with After {
    def after = system.shutdown()
  }

  
  "RequestQueue" should {

    "handle one request" in new ActorSystemContext {
      val requestQueue = TestActorRef(new RequestQueue(client, MaxInFlightRequests))
      requestQueue ! AddToEnd("url")

      client.completeAllRequests(response)
      expectMsgType[Result](1 seconds)
    }

    "only have N requests inflight at a time" in new ActorSystemContext {
      val requestQueue = TestActorRef(new RequestQueue(client, MaxInFlightRequests))
      
      1 to (MaxInFlightRequests + 1) foreach {_ => requestQueue ! AddToEnd("url") }

      client.requestsInFlight must be equalTo(MaxInFlightRequests)
      
      client.completeNext(response)
      client.requestsInFlight must be equalTo(MaxInFlightRequests)
      
      client.completeAllRequests(response)

      client.requestsInFlight must be equalTo(0)
    }

  }
}
