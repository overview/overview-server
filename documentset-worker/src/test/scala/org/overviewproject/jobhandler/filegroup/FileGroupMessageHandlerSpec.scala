package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import scala.concurrent.Future
import scala.util.Try
import scala.util.Success
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.filegroup.FileGroupMessageHandlerProtocol._
import akka.actor._
import org.overviewproject.test.ForwardingActor
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.TextExtractorProtocol.ExtractText
import org.overviewproject.jobhandler.JobProtocol._
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.MessageHandlerProtocol._
import akka.testkit.TestFSMRef
import org.overviewproject.jobhandler.filegroup.FileGroupMessageHandlerFSM._

class DummyActor extends Actor {
  def receive = {
    case ExtractText(fileGroupId, uploadedFileId) => {
      sender ! JobDone(fileGroupId)
      context.stop(self)
    }
  }
}

class FailingActor extends Actor {
  def receive = {
// FIXME: If we actually throw an exception, the unit tests print ugly stack traces
// In Akka 2.2 we will be able to turn off logging by setting loggingEnabled = false
// on the SupervisorStrategy
//    case _ => throw(new Exception("Fail"))
    case _ => context.stop(self)
  }
}

class FileGroupMessageHandlerSpec extends Specification with Mockito {

  "FileGroupMessageHandler" should {

    class TestMessageHandler(jobMonitor: ActorRef) extends FileGroupMessageHandler(jobMonitor) with FileGroupMessageHandlerComponent {

      override val storage = mock[Storage]
      override val actorCreator = mock[ActorCreator]
      actorCreator.produceTextExtractor returns Props[DummyActor]
    }

    class FailingMessageHandler(jobMonitor: ActorRef) extends FileGroupMessageHandler(jobMonitor) with FileGroupMessageHandlerComponent {

      override val storage = mock[Storage]
      override val actorCreator = mock[ActorCreator]
      actorCreator.produceTextExtractor returns Props[FailingActor]
    }
    
    "start file handler on incoming command" in new ActorSystemContext {
      val jobMonitor = TestProbe()
      val command = ProcessFileCommand(1l, 10l)

      val messageHandler = TestActorRef(new TestMessageHandler(jobMonitor.ref))

      messageHandler ! command
      val actorCreator = messageHandler.underlyingActor.actorCreator

      there was one(actorCreator).produceTextExtractor
    }

    "forward JobDone to monitor" in new ActorSystemContext {
      val requestor = TestProbe()
      val jobMonitor = TestProbe()
      val fileGroupId = 1l
      val command = ProcessFileCommand(fileGroupId, 10l)

      val messageHandler = TestActorRef(new TestMessageHandler(jobMonitor.ref))

      messageHandler ! command

      jobMonitor.expectMsg(JobDone(fileGroupId))
    }
    
    "set ProcessedFile state to Error if processing fails" in new ActorSystemContext {
      val jobMonitor = TestProbe()
      val fileGroupId = 1l
      val uploadedFileId = 10l
      val command = ProcessFileCommand(fileGroupId, uploadedFileId)

      val messageHandler = TestActorRef(new FailingMessageHandler(jobMonitor.ref))
      
      messageHandler ! command
      
      jobMonitor.expectMsg(JobDone(fileGroupId))  

      there was one(messageHandler.underlyingActor.storage).writeFileInErrorState(fileGroupId, uploadedFileId)
    }
  }
}
