package com.overviewdocs.jobhandler.filegroup

import akka.actor.{ActorRef,ActorSystem}
import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mutable.Specification
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future}
import scala.concurrent.duration.Duration

import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class AddDocumentsWorkBrokerSpec extends Specification {
  sequential

  import AddDocumentsWorkBroker._
  import AddDocumentsWorker._

  /** AddDocumentsWorkBroker with a mock loadWorkGeneratorForCommand(). */
  class TestSubject extends AddDocumentsWorkBroker {
    val jobs: mutable.Map[AddDocumentsFromFileGroup,Seq[GroupedFileUpload]] = mutable.Map()

    /** Add stub data, then send DoWorkThenAck. */
    def startTestWork(command: AddDocumentsFromFileGroup, nUploads: Int, ackTarget: ActorRef, ackMessage: Any): Unit = {
      val uploads = Seq.tabulate(nUploads) { i => factory.groupedFileUpload(fileGroupId=command.fileGroupId) }
      jobs.+=(command -> uploads)
      self ! DoWorkThenAck(command, ackTarget, ackMessage)
    }

    /** Use a dummy ack ActorRef. */
    def startTestWork(command: AddDocumentsFromFileGroup, nUploads: Int)(implicit system: ActorSystem): Unit = {
      startTestWork(command, nUploads, TestProbe().ref, ())
    }

    override protected def loadWorkGeneratorForCommand(command: AddDocumentsFromFileGroup)(implicit ec: ExecutionContext) = {
      val uploads = jobs(command)
      Future.successful(new AddDocumentsWorkGenerator(command, uploads))
    }
  }

  "AddDocumentsWorkBroker" should {
    trait BaseScope extends ActorSystemContext {
      val subject = TestActorRef(new TestSubject) // TestActorRef: messages are synchronous
      val broker: TestSubject = subject.underlyingActor

      val command1 = AddDocumentsFromFileGroup(1L, 2L, 3L, "fr", true)
      val command2 = AddDocumentsFromFileGroup(4L, 5L, 6L, "sw", false)
      val command3 = AddDocumentsFromFileGroup(7L, 8L, 9L, "de", false)

      def handleUpload(command: AddDocumentsFromFileGroup, index: Int): HandleUpload = {
        HandleUpload(command, broker.jobs(command)(index))
      }
    }

    "give a job to a sender if there is one pending" in new BaseScope {
      broker.startTestWork(command1, 1)
      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))
    }

    "give a job to a sender if if it comes in after the job does" in new BaseScope {
      subject ! WorkerReady
      broker.startTestWork(command1, 1)
      expectMsg(handleUpload(command1, 0))
    }

    "give work messages to all waiting workers when a job arrives" in new BaseScope {
      subject ! WorkerReady
      subject ! WorkerReady
      broker.startTestWork(command1, 2)
      expectMsg(handleUpload(command1, 0))
      expectMsg(handleUpload(command1, 1))
    }

    "give work messages to two workers for the same job" in new BaseScope {
      broker.startTestWork(command1, 2)
      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))
      subject ! WorkerReady
      expectMsg(handleUpload(command1, 1))
    }

    "handle work in round-robin fashion" in new BaseScope {
      broker.startTestWork(command1, 2)
      broker.startTestWork(command2, 2)
      broker.startTestWork(command3, 2)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! WorkerReady
      expectMsg(handleUpload(command2, 0))

      subject ! WorkerReady
      expectMsg(handleUpload(command3, 0))

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 1))
    }

    "skip a generator when it has no work" in new BaseScope {
      broker.startTestWork(command1, 1) // second call to .nextWork will return NoWorkForNow
      broker.startTestWork(command2, 2)
      broker.startTestWork(command3, 2)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! WorkerReady
      expectMsg(handleUpload(command2, 0))

      subject ! WorkerReady
      expectMsg(handleUpload(command3, 0))

      subject ! WorkerReady
      expectMsg(handleUpload(command2, 1)) // skipped command1
    }

    "resume a work generator when it gets work and a worker is waiting" in new BaseScope {
      broker.startTestWork(command1, 1)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! WorkerReady
      expectNoMsg(Duration.Zero)

      subject ! WorkerDoneHandleUpload(command1)
      expectMsg(FinishJob(command1))
    }

    "resume a work generator when it gets finished and then a worker arrives" in new BaseScope {
      broker.startTestWork(command1, 1)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! WorkerDoneHandleUpload(command1)

      subject ! WorkerReady
      expectMsg(FinishJob(command1))
    }

    "send cancel messages to workers that are working on a job" in new BaseScope {
      broker.startTestWork(command1, 2)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! CancelJob(command1.documentSetCreationJobId)
      expectMsg(CancelHandleUpload(command1))
    }

    "not crash when cancelling a job that is already finished" in new BaseScope {
      broker.startTestWork(command1, 0)

      subject ! WorkerReady
      expectMsg(FinishJob(command1))
      subject ! WorkerDoneFinishJob(command1) // the broker forgets about the job now

      subject ! CancelJob(command1.documentSetCreationJobId)
      expectNoMsg(Duration.Zero)

      subject ! WorkerReady
      expectNoMsg(Duration.Zero)
    }

    "still send FinishJob after CancelHandleUpload" in new BaseScope {
      broker.startTestWork(command1, 2)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! CancelJob(command1.documentSetCreationJobId)
      expectMsg(CancelHandleUpload(command1))

      subject ! WorkerDoneHandleUpload(command1)
      subject ! WorkerReady

      expectMsg(FinishJob(command1))
    }

    "wait on FinishJob until cancelled uploads are finished" in new BaseScope {
      broker.startTestWork(command1, 2)

      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))

      subject ! CancelJob(command1.documentSetCreationJobId)
      expectMsg(CancelHandleUpload(command1))

      // some other worker comes along...
      subject ! WorkerReady
      expectNoMsg(Duration.Zero)

      // and now the first worker says it finished the cancelled HandleUpload
      subject ! WorkerDoneHandleUpload(command1)
      expectMsg(FinishJob(command1)) // the broker sends somebody the FinishJob
    }

    "only cancel jobs for workers that are processing them" in new BaseScope {
      // In other words: if a worker has never seen a job, don't message it.
      broker.startTestWork(command1, 2)
      broker.startTestWork(command2, 2)

      val worker1 = TestProbe()
      val worker2 = TestProbe()

      subject.tell(WorkerReady, worker1.ref) // worker1: command1
      subject.tell(WorkerReady, worker2.ref) // worker2: command2

      worker1.expectMsg(handleUpload(command1, 0))
      worker2.expectMsg(handleUpload(command2, 0))

      subject ! CancelJob(command1.documentSetCreationJobId)

      worker1.expectMsg(CancelHandleUpload(command1))
      worker2.expectNoMsg(Duration.Zero)
    }

    "not cancel jobs on a worker that has said it is not working on that job" in new BaseScope {
      // In other words: after a worker has said it is done with a job, don't message it.
      broker.startTestWork(command1, 2)
      broker.startTestWork(command2, 2)

      // a worker starts and finishes with a HandleUpload for command1...
      subject ! WorkerReady
      expectMsg(handleUpload(command1, 0))
      subject ! WorkerDoneHandleUpload(command1)

      // the worker starts on a HandleUpload for command2...
      subject ! WorkerReady
      expectMsg(handleUpload(command2, 0))

      subject ! CancelJob(command1.documentSetCreationJobId)
      expectNoMsg(Duration.Zero) // the worker isn't working on command1, so it gets no message
    }

    "send an ack after WorkerDoneFinishJob" in new BaseScope {
      val probe = TestProbe()

      broker.startTestWork(command1, 0, probe.ref, "ack")

      subject ! WorkerReady
      expectMsg(FinishJob(command1))
      subject ! WorkerDoneFinishJob(command1)
      probe.expectMsg("ack")
    }
  }
}
