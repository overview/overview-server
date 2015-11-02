package com.overviewdocs.jobhandler.documentset

import akka.actor.Props
import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.duration.Duration
import scala.concurrent.{Future,Promise}

import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.filegroup.AddDocumentsWorkBroker
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentSetCommandWorkerSpec extends Specification with Mockito {
  sequential

  import DocumentSetMessageBroker._

  "DocumentSetCommandWorker" should {
    trait BaseScope extends ActorSystemContext {
      val broker = TestProbe()
      val addDocumentsWorkBroker = TestProbe()
      val documentSetDeleter = smartMock[DocumentSetDeleter]
      val subject = TestActorRef(DocumentSetCommandWorker.props(broker.ref, addDocumentsWorkBroker.ref, documentSetDeleter))
    }

    "send WorkerReady on start" in new BaseScope {
      broker.expectMsg(WorkerReady)
    }

    "AddDocumentsFromFileGroup" should {
      "send WorkerReady immediately" in new BaseScope {
        broker.expectMsg(WorkerReady)

        subject ! DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(addToDocumentSetId=Some(1L)))
        broker.expectMsg(WorkerReady)
      }

      "queue ack message for when command completes" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val command = DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(addToDocumentSetId=Some(2L)))
        subject ! command
        // This is one-half of the spec; the other half is in AddDocumentsWorkBroker
        addDocumentsWorkBroker.expectMsg(
          AddDocumentsWorkBroker.DoWorkThenAck(command, broker.ref, WorkerDoneDocumentSetCommand(2L))
        )
      }
    }

    "DeleteDocumentSet" should {
      "call documentSetDeleter.delete" in new BaseScope {
        documentSetDeleter.delete(1L) returns Future.successful(())
        subject ! DocumentSetCommands.DeleteDocumentSet(1L)
        there was one(documentSetDeleter).delete(1L)
      }

      "return to the broker when complete" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val promise = Promise[Unit]()
        documentSetDeleter.delete(1L) returns promise.future
        subject ! DocumentSetCommands.DeleteDocumentSet(1L)

        broker.expectNoMsg(Duration.Zero)

        promise.success(())
        broker.expectMsg(WorkerDoneDocumentSetCommand(1L))
        broker.expectMsg(WorkerReady)
      }
    }

    "CancelJob" should {
      "forward to addDocumentsWorkBroker" in new BaseScope {
        subject ! DocumentSetCommands.CancelAddDocumentsFromFileGroup(1L, 2L)
        addDocumentsWorkBroker.expectMsg(AddDocumentsWorkBroker.CancelJob(2L))
      }
    }
  }
}
