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
import org.overviewproject.jobhandler.filegroup.FileHandlerProtocol.ExtractText
import org.overviewproject.jobhandler.MessageServiceComponent
import org.overviewproject.jobhandler.JobProtocol._
import org.specs2.mock.Mockito

class DummyActor extends Actor {
  def receive = {
    case _ =>
  }
}

class FileGroupJobHandlerSpec extends Specification with Mockito {

  "FileGroupJobHandler" should {

    class TestMessageHandler(jobMonitor: ActorRef) extends FileGroupMessageHandler(jobMonitor) with TextExtractorComponent {

      val actorCreator = mock[ActorCreator]
      actorCreator.produceTextExtractor returns Props[DummyActor]

    }

    "start file handler on incoming command" in new ActorSystemContext {
      val jobMonitor = TestProbe()
      val command = ProcessFileCommand(1l, 10l)

      val messageHandler = TestActorRef(new TestMessageHandler(jobMonitor.ref))

      messageHandler ! command
      val actorCreator = messageHandler.underlyingActor.actorCreator

      there was one(actorCreator).produceTextExtractor
    }

    "send JobStart to parent when message is received" in new ActorSystemContext {
      val jobMonitor = TestProbe()
      val fileGroupId = 1l
      val command = ProcessFileCommand(fileGroupId, 10l)

      val parentProbe = TestProbe()
      val messageHandler = TestActorRef(Props(new TestMessageHandler(jobMonitor.ref)), parentProbe.ref, "message handler")

      messageHandler ! command
      
      parentProbe.expectMsg(JobStart(fileGroupId))
    }
    
    "forward JobDone to parent" in new ActorSystemContext {
      val jobMonitor = TestProbe()
      val fileGroupId = 1l

      val parentProbe = TestProbe()
      val messageHandler = TestActorRef(Props(new TestMessageHandler(jobMonitor.ref)), parentProbe.ref, "message handler")

      messageHandler ! JobDone(fileGroupId)

      parentProbe.expectMsg(JobDone(fileGroupId))
    }
  }
}