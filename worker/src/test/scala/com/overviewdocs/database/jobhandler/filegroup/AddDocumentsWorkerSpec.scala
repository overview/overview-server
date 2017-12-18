package com.overviewdocs.jobhandler.filegroup

import akka.actor.UnhandledMessage
import akka.testkit.{TestActorRef,TestProbe}
import org.mockito.Matchers
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
      impl.processUpload(any, any, any)(any) returns Future.unit
      val upload = makeUpload
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      there was one(impl).processUpload(===(fileGroup), ===(upload), any)(===(subject.dispatcher))
    }

    "crash when impl returns a Failure" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val ex = new Exception("foo")
      impl.processUpload(any, any, any)(any) returns Future.failed(ex)
      val upload = makeUpload
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[UnhandledMessage])
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      probe.expectMsg(UnhandledMessage(ex, subject, subject))
    }

    "update progress" in new BaseScope {
      var progressRetval: Boolean = _
      broker.expectMsg(WorkerReady)
      impl.processUpload(any, any, any)(any) answers { (arguments, _) =>
        progressRetval = arguments.asInstanceOf[Array[Any]](2).asInstanceOf[Double=>Boolean](0.4)
        Future.unit
      }
      val upload = makeUpload
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      broker.expectMsg(WorkerHandleUploadProgress(fileGroup, upload, 0.4))
      progressRetval must beEqualTo(true)
    }

    "tell impl to cancel via progress-update callback" in new BaseScope {
      import scala.concurrent.ExecutionContext.Implicits.global
      var progressRetval: Boolean = _
      val promise = Promise[Unit]()
      val upload = makeUpload
      broker.expectMsg(WorkerReady)
      impl.processUpload(any, any, any)(any) answers { (arguments, _) =>
        for { _ <- promise.future }
        yield {
          progressRetval = arguments.asInstanceOf[Array[Any]](2).asInstanceOf[Double=>Boolean](0.4)
          ()
        }
      }
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      subject.tell(CancelHandleUpload(fileGroup), broker.ref)
      promise.success(())
      broker.expectMsg(WorkerHandleUploadProgress(fileGroup, upload, 0.4))
      progressRetval must beEqualTo(false)
    }

    "send WorkerDoneHandleUpload when done work" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      val upload = makeUpload
      impl.processUpload(any, any, any)(any) returns promise.future
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneHandleUpload(fileGroup, upload))
    }

    "behave as usual when receiving CancelHandleUpload" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val promise = Promise[Unit]()
      val upload = makeUpload
      impl.processUpload(any, any, any)(any) returns promise.future
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      subject.tell(CancelHandleUpload(fileGroup), broker.ref)
      broker.expectNoMsg(Duration.Zero)
      promise.success(())
      broker.expectMsg(WorkerDoneHandleUpload(fileGroup, upload))
    }

    "send WorkerReady when done work" in new BaseScope {
      broker.expectMsg(WorkerReady)
      val upload = makeUpload
      impl.processUpload(any, any, any)(any) returns Future.unit
      subject.tell(HandleUpload(fileGroup, upload), broker.ref)
      broker.expectMsg(WorkerDoneHandleUpload(fileGroup, upload))
      broker.expectMsg(WorkerReady)
    }

    "handle a FinishJob message" in new BaseScope {
      impl.finishJob(fileGroup)(subject.dispatcher) returns Future.unit
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
