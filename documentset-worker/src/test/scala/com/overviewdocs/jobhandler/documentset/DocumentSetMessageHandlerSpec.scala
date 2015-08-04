package com.overviewdocs.jobhandler.documentset

import akka.actor._
import akka.testkit._
import com.overviewdocs.jobhandler.JobProtocol._
import com.overviewdocs.messagequeue.MessageHandlerProtocol._
import com.overviewdocs.jobhandler.documentset.DeleteHandlerProtocol._
import com.overviewdocs.jobhandler.documentset.DocumentSetJobHandlerProtocol._
import com.overviewdocs.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mutable.Specification
import org.specs2.mutable.Before


class DocumentSetMessageHandlerSpec extends Specification {
  
  private val fileGroupRemovalQueuePath = "ignored path"

  class TestMessageHandler(specificHandlerProbe: ActorRef)
  extends DocumentSetMessageHandler(fileGroupRemovalQueuePath) with SearchComponent {

    val actorCreator = new ActorCreator {
      override def produceDeleteHandler(fileRemovalQueuePath: String): Actor =
        new ForwardingActor(specificHandlerProbe)
    }
  }

  class FailingActor extends Actor {
    def receive = {
      case _ => context.stop(self)
    }
  }

  class FailingTestMessagHandler extends DocumentSetMessageHandler(fileGroupRemovalQueuePath) with SearchComponent {

    val actorCreator = new ActorCreator {
      override def produceDeleteHandler(fileRemovalQueuePath: String): Actor = new FailingActor
    }
  }

  "DocumentSetMessageHandler" should {

    trait DocumentSetInfo {
      val documentSetId = 1l
      val jobId = 2L
    }

    trait DeleteInfo extends DocumentSetInfo {
      val deleteCommand = DeleteCommand(documentSetId, false)
      val deleteMessage = DeleteDocumentSet(documentSetId, false)

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

    "start delete handler" in new DeleteContext {
      messageHandler ! deleteCommand

      deleteHandler.expectMsg(deleteMessage)
    }
    
    "start delete handler and send delete tree job command" in new DeleteContext {
      messageHandler ! DeleteTreeJobCommand(jobId)
      
      deleteHandler.expectMsg(DeleteReclusteringJob(jobId))
    }

    "tell parent MessageHandled when JobDone is received" in new DeleteWithParentContext {
      messageHandler ! deleteCommand
      messageHandler ! JobDone(documentSetId)

      parent.expectMsg(MessageHandled)
    }

    // TODO: We need proper error recovery
    // If we don't ack the message, it keeps getting resent, potentially causing the same error
    "tell parent MessageHandled if an message handler dies unexpectedly" in new FailingMessageHandlerWithParentContext {
      messageHandler ! DeleteCommand(documentSetId, false)

      parent.expectMsg(MessageHandled)
    }
  }

}
