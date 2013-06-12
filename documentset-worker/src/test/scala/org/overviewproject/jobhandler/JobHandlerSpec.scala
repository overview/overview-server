package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import akka.actor.ActorRef
import akka.actor.Actor
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.JobHandlerProtocol._
import org.overviewproject.jobhandler.SearchHandlerProtocol.SearchDocumentSet
import javax.jms.TextMessage
import org.specs2.specification.Scope

class JobHandlerSpec extends Specification with Mockito {

  "JobHandler" should {

    class TestSearchHandler(searchProbe: ActorRef, requestQueue: ActorRef, messageText: String) extends JobHandler(requestQueue) with MessageServiceComponent with SearchComponent {
      override val messageService = mock[MessageService]

      val message = mock[TextMessage]
      message.getText returns messageText

      val actorCreator = new ActorCreator {
        override def produceSearchHandler: Actor = new ForwardingActor(searchProbe)
      }
    }

    class ForwardingActor(target: ActorRef) extends Actor {
      def receive = {
        case msg => target forward msg
      }
    }

    trait MessageSetup extends Scope {
      val documentSetId = 5l
      val query = "projectid:333 search terms"

      val commandMessage = s"""
      {
        "cmd" : "search",
        "args" : {
          "documentSetId" : $documentSetId,
          "query" : "$query"
        }
      }"""
    }

    "start listening for messages" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))

      jobHandler ! StartListening

      val messageService = jobHandler.underlyingActor.messageService

      there was one(messageService).createConnection(any, any)
    }

    "start search handler on incoming search command" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))

      jobHandler ! StartListening
      jobHandler ! SearchCommand(documentSetId, query)

      searchHandler.expectMsg(SearchDocumentSet(documentSetId, query, testActor))
    }

    "handle next search command when done with previous" in new ActorSystemContext with MessageSetup {
      val query2 = s"$query more search terms"
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))

      jobHandler ! StartListening
      jobHandler ! SearchCommand(documentSetId, query)
      jobHandler ! JobDone
      jobHandler ! SearchCommand(documentSetId, query2)
      searchHandler.expectMsg(SearchDocumentSet(documentSetId, query, testActor))
      searchHandler.expectMsg(SearchDocumentSet(documentSetId, query2, testActor))

    }
  }
}