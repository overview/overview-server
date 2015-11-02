package com.overviewdocs.jobhandler.filegroup

import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration
import scala.concurrent.{Future,Promise}

import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class AddDocumentsWorkerSpec extends Specification with Mockito {
  sequential

  import AddDocumentsWorkBroker._
  import AddDocumentsWorker._

  "AddDocumentsWorker" should {
    trait BaseScope extends ActorSystemContext {
      val impl = smartMock[AddDocumentsImpl]
      val broker = TestProbe()
      val subject = TestActorRef(AddDocumentsWorker.props(broker.ref, impl))

      val fileGroup = factory.fileGroup(id=1L, addToDocumentSetId=Some(2L), lang=Some("fr"), splitDocuments=Some(true))
      def makeUpload = factory.groupedFileUpload(fileGroupId=fileGroup.id)
    }

    "ask for work on startup" in new BaseScope {
      broker.expectMsg(WorkerReady)
    }

    "do work" in new BaseScope {
      impl.processUpload(any, any)(any) returns Future.successful(())
      val upload = makeUpload
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      there was one(impl).processUpload(fileGroup, upload)(subject.dispatcher)
    }

    "send WorkerDoneHandleUpload when done work" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      val upload = makeUpload
      impl.processUpload(any, any)(any) returns promise.future
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneHandleUpload(fileGroup, upload))
    }

    "behave as usual when receiving CancelHandleUpload" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      val upload = makeUpload
      impl.processUpload(any, any)(any) returns promise.future
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      subject.tell(CancelHandleUpload(fileGroup), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneHandleUpload(fileGroup, upload))
    }

    "send WorkerReady when done work" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val upload = makeUpload
      impl.processUpload(any, any)(any) returns Future.successful(())
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      broker.expectMsg(WorkerDoneHandleUpload(fileGroup, upload))
      broker.expectMsg(WorkerReady)
    }

    "handle a FinishJob message" in new BaseScope {
      impl.finishJob(fileGroup)(subject.dispatcher) returns Future.successful(())
      subject.tell(FinishJob(fileGroup), broker.ref)
      there was one(impl).finishJob(fileGroup)(subject.dispatcher)
    }

    "send WorkerDoneFinishJob and WorkerReady when after a FinishJob" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      impl.finishJob(any)(any) returns promise.future
      subject.tell(FinishJob(fileGroup), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneFinishJob(fileGroup))
      broker.expectMsg(WorkerReady)
    }
  }
}
