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
      val worker1 = TestProbe()
      val worker2 = TestProbe()

      object msg {
        def Delete(documentSetId: Long) = DocumentSetCommands.DeleteDocumentSet(documentSetId)
        def Done(documentSetId: Long) = DocumentSetMessageBroker.WorkerDoneDocumentSetCommand(documentSetId)
        def Ready = DocumentSetMessageBroker.WorkerReady
        def Add(documentSetId: Long) = DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(id=1L, addToDocumentSetId=Some(documentSetId)))
        def CancelAdd(documentSetId: Long) = DocumentSetCommands.CancelAddDocumentsFromFileGroup(documentSetId, 1L)
      }
    }

    "forward a Command to a worker when the command arrives first" in new BaseScope {
      subject ! msg.Delete(123L)
      subject.tell(msg.Ready, worker1.ref)
      worker1.expectMsg(msg.Delete(123L))
    }

    "forward a Command to a worker when the worker arrives first" in new BaseScope {
      subject.tell(msg.Ready, worker1.ref)
      subject ! msg.Delete(123L)
      worker1.expectMsg(msg.Delete(123L))
    }

    "queue the workers" in new BaseScope {
      subject.tell(msg.Ready, worker1.ref)
      subject.tell(msg.Ready, worker2.ref)
      subject ! msg.Delete(123L)
      subject ! msg.Delete(234L)
      worker1.expectMsg(msg.Delete(123L))
      worker2.expectMsg(msg.Delete(234L))
    }

    "queue the commands" in new BaseScope {
      subject ! msg.Delete(123L)
      subject ! msg.Delete(234L)
      subject.tell(msg.Ready, worker1.ref)
      subject.tell(msg.Ready, worker2.ref)
      worker1.expectMsg(msg.Delete(123L))
      worker2.expectMsg(msg.Delete(234L))
    }

    "forward a CancelCommand to only the worker that is handling it" in new BaseScope {
      subject.tell(msg.Ready, worker1.ref)
      subject.tell(msg.Ready, worker2.ref)
      subject ! msg.Add(123L)
      worker1.expectMsg(msg.Add(123L))
      subject ! msg.CancelAdd(123L)
      worker1.expectMsg(msg.CancelAdd(123L))
      worker2.expectNoMsg(Duration.Zero)
    }

    "not forward a CancelCommand to a worker that is not handling it any more" in new BaseScope {
      subject.tell(msg.Ready, worker1.ref)
      subject ! msg.Add(123L)
      worker1.expectMsg(msg.Add(123L))
      subject.tell(msg.Done(123L), worker1.ref)
      subject ! msg.CancelAdd(123L)
      worker1.expectNoMsg(Duration.Zero)
    }

    "run only one command per document set at a time" in new BaseScope {
      subject.tell(msg.Ready, worker1.ref)
      subject.tell(msg.Ready, worker2.ref)
      subject ! msg.Add(123L)
      subject ! msg.Delete(123L)
      worker1.expectMsg(msg.Add(123L))
      worker2.expectNoMsg(Duration.Zero)
      subject.tell(msg.Done(123L), worker1.ref)
      worker2.expectMsg(msg.Delete(123L))
    }

    "send two commands to the same worker, if it asks" in new BaseScope {
      subject.tell(msg.Ready, worker1.ref)
      subject ! msg.Add(123L)
      worker1.expectMsg(msg.Add(123L))
      subject.tell(msg.Ready, worker1.ref)
      subject ! msg.Add(234L)
      worker1.expectMsg(msg.Add(234L))
    }
  }
}
