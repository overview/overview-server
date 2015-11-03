package com.overviewdocs.jobhandler.documentset

import akka.actor.Props
import akka.pattern.ask
import akka.testkit.TestProbe
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.duration.Duration

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentSetMessageBrokerSpec extends Specification {
  sequential

  "DocumentSetMessageBroker" should {
    trait BaseScope extends ActorSystemContext {
      val subject = system.actorOf(Props[DocumentSetMessageBroker])
    }

    "forward a Command to a worker when the command arrives first" in new BaseScope {
      val command = DocumentSetCommands.DeleteDocumentSet(123L)
      subject ! command
      (subject ? DocumentSetMessageBroker.WorkerReady) must beEqualTo(command).await
    }

    "forward a Command to a worker when the worker arrives first" in new BaseScope {
      val command = DocumentSetCommands.DeleteDocumentSet(123L)
      val future = (subject ? DocumentSetMessageBroker.WorkerReady)
      subject ! command
      future must beEqualTo(command).await
    }

    "queue the workers" in new BaseScope {
      val command1 = DocumentSetCommands.DeleteDocumentSet(1L)
      val command2 = DocumentSetCommands.DeleteDocumentSet(2L)
      val future1 = (subject ? DocumentSetMessageBroker.WorkerReady)
      val future2 = (subject ? DocumentSetMessageBroker.WorkerReady)
      subject ! command1
      future1 must beEqualTo(command1).await
      subject ! command2
      future2 must beEqualTo(command2).await
    }

    "queue the commands" in new BaseScope {
      val command1 = DocumentSetCommands.DeleteDocumentSet(1L)
      val command2 = DocumentSetCommands.DeleteDocumentSet(2L)
      subject ! command1
      subject ! command2
      (subject ? DocumentSetMessageBroker.WorkerReady) must beEqualTo(command1).await
      (subject ? DocumentSetMessageBroker.WorkerReady) must beEqualTo(command2).await
    }

    "forward a CancelCommand to the worker that is handling it" in new BaseScope {
      val worker = TestProbe()
      val command1 = DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(id=1L, addToDocumentSetId=Some(2L)))
      val command2 = DocumentSetCommands.CancelAddDocumentsFromFileGroup(2L, 1L)
      subject ! command1
      subject.tell(DocumentSetMessageBroker.WorkerReady, worker.ref)
      worker.expectMsg(command1)
      subject ! command2
      worker.expectMsg(command2)
    }

    "not forward a CancelCommand to a worker that is not handling it any more" in new BaseScope {
      val worker = TestProbe()
      val command1 = DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(id=1L, addToDocumentSetId=Some(2L)))
      val command2 = DocumentSetCommands.CancelAddDocumentsFromFileGroup(2L, 1L)
      subject ! command1
      subject.tell(DocumentSetMessageBroker.WorkerReady, worker.ref)
      worker.expectMsg(command1)
      subject.tell(DocumentSetMessageBroker.WorkerDoneDocumentSetCommand(2L), worker.ref)
      subject ! command2
      worker.expectNoMsg(Duration.Zero)
    }
  }
}
