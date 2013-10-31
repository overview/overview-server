package org.overviewproject.jobhandler.documentset

import akka.actor._
import akka.testkit._
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.messagequeue.MessageHandlerProtocol._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol._
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mutable.Specification
import org.specs2.mutable.Before

class DocumentSetMessageHandlerSpec extends Specification {

  class TestMessageHandler(specificHandlerProbe: ActorRef) extends DocumentSetMessageHandler
      with SearchComponent {

    val actorCreator = new ActorCreator {
      override def produceSearchHandler: Actor = new ForwardingActor(specificHandlerProbe)
      override def produceDeleteHandler: Actor = new ForwardingActor(specificHandlerProbe)
    }
  }

  class FailingActor extends Actor {
    def receive = {
      case _ => context.stop(self)
    }
  }

  class FailingTestMessagHandler extends DocumentSetMessageHandler with SearchComponent {

    val actorCreator = new ActorCreator {
      override def produceSearchHandler: Actor = new FailingActor
      override def produceDeleteHandler: Actor = new FailingActor
    }
  }

  "DocumentSetMessageHandler" should {

    trait DocumentSetInfo {
      val documentSetId = 1l
    }

    abstract class SearchContext extends ActorSystemContext with DocumentSetInfo with Before {
      val query = "query string"
      val searchCommand = SearchCommand(documentSetId, query)
      val searchMessage = SearchDocumentSet(documentSetId, query)

      var searchHandler: TestProbe = _
      var messageHandler: TestActorRef[TestMessageHandler] = _

      def before = {
        searchHandler = TestProbe()
        messageHandler = TestActorRef(new TestMessageHandler(searchHandler.ref))
      }
    }

    trait DeleteInfo extends DocumentSetInfo {
      val deleteCommand = DeleteCommand(documentSetId)
      val deleteMessage = DeleteDocumentSet(documentSetId)

      var deleteHandler: TestProbe = _
    }

    abstract class DeleteContext extends ActorSystemContext with DeleteInfo with Before {
      var messageHandler: TestActorRef[TestMessageHandler] = _

      def before = {
        deleteHandler = TestProbe()
        messageHandler = TestActorRef(new TestMessageHandler(deleteHandler.ref))
      }
    }

    abstract class DeleteWithParentContext extends ActorSystemContext with DeleteInfo with Before {
      var parent: TestProbe = _
      var messageHandler: TestActorRef[Nothing] = _

      def before = {
        parent = TestProbe()
        deleteHandler = TestProbe()
        messageHandler = TestActorRef(Props(new TestMessageHandler(deleteHandler.ref)), parent.ref, "Message Handler")
      }
    }

    abstract class FailingMessageHandlerWithParentContext extends ActorSystemContext with DocumentSetInfo with Before {
      var parent: TestProbe = _
      var messageHandler: TestActorRef[Nothing] = _

      def before = {
        parent = TestProbe()

        messageHandler = TestActorRef(Props(new FailingTestMessagHandler), parent.ref, "Message Handler")

      }
    }

    "start search handler" in new SearchContext {
      messageHandler ! searchCommand

      searchHandler.expectMsg(searchMessage)
    }

    "start delete handler" in new DeleteContext {
      messageHandler ! deleteCommand

      deleteHandler.expectMsg(deleteMessage)
    }

    "tell parent MessageHandled when JobDone is received" in new DeleteWithParentContext {
      messageHandler ! deleteCommand
      messageHandler ! JobDone(documentSetId)

      parent.expectMsg(MessageHandled)
    }

    // TODO: We need proper error recovery
    // If we don't ack the message, it keeps getting resent, potentially causing the same error
    "tell parent MessageHandled if an message handler dies unexpectedly" in new FailingMessageHandlerWithParentContext {
      messageHandler ! DeleteCommand(documentSetId)

      parent.expectMsg(MessageHandled)
    }
  }

}