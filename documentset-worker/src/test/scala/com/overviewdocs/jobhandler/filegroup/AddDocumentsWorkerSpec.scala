package com.overviewdocs.jobhandler.filegroup

import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration
import scala.concurrent.{Future,Promise}

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

      val job = AddDocumentsJob(1L, 2L, 3L, "fr", true)
      def makeUpload = factory.groupedFileUpload(fileGroupId=job.fileGroupId)
    }

    "ask for work on startup" in new BaseScope {
      broker.expectMsg(WorkerReady)
    }

    "do work" in new BaseScope {
      impl.processUpload(any, any)(any) returns Future.successful(())
      val upload = makeUpload
      subject.tell(HandleUpload(job, upload), broker.ref)
      there was one(impl).processUpload(job, upload)(subject.dispatcher)
    }

    "send WorkerDoneHandleUpload when done work" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      impl.processUpload(any, any)(any) returns promise.future
      subject.tell(HandleUpload(job, makeUpload), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneHandleUpload(job))
    }

    "behave as usual when receiving CancelHandleUpload" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      impl.processUpload(any, any)(any) returns promise.future
      subject.tell(HandleUpload(job, makeUpload), broker.ref)
      subject.tell(CancelHandleUpload(job), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneHandleUpload(job))
    }

    "send WorkerReady when done work" in new BaseScope {
      broker.expectMsg(WorkerReady)
      impl.processUpload(any, any)(any) returns Future.successful(())
      subject.tell(HandleUpload(job, makeUpload), broker.ref)
      broker.expectMsg(WorkerDoneHandleUpload(job))
      broker.expectMsg(WorkerReady)
    }

    "handle a FinishJob message" in new BaseScope {
      impl.finishJob(job)(subject.dispatcher) returns Future.successful(())
      subject.tell(FinishJob(job), broker.ref)
      there was one(impl).finishJob(job)(subject.dispatcher)
    }

    "send WorkerReady when after a FinishJob" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      impl.finishJob(any)(any) returns promise.future
      subject.tell(FinishJob(job), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerReady)
    }
  }
}
