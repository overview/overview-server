package com.overviewdocs.jobhandler.filegroup

import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration

import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class AddDocumentsWorkBrokerSpec extends Specification {
  sequential

  import AddDocumentsWorkBroker._
  import AddDocumentsWorker._

  "AddDocumentsWorkBroker" should {
    trait BaseScope extends ActorSystemContext {
      val subject = TestActorRef(AddDocumentsWorkBroker.props) // TestActorRef: messages are synchronous

      val job1 = AddDocumentsJob(1L, 2L, 3L, "fr", true)
      val job2 = AddDocumentsJob(4L, 5L, 6L, "sw", false)
      val job3 = AddDocumentsJob(7L, 8L, 9L, "de", false)

      def generator(job: AddDocumentsJob, nUploads: Int) = new AddDocumentsWorkGenerator(
        job,
        Seq.tabulate(nUploads) { i => factory.groupedFileUpload(fileGroupId=job.fileGroupId) }
      )
    }

    "give a job to a sender if there is one pending" in new BaseScope {
      val gen1 = generator(job1, 1)
      subject ! AddWorkGenerator(gen1)
      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))
    }

    "give a job to a sender if if it comes in after the job does" in new BaseScope {
      val gen1 = generator(job1, 1)
      subject ! WorkerReady
      subject ! AddWorkGenerator(gen1)
      expectMsg(HandleUpload(job1, gen1.uploads(0)))
    }

    "give work messages to all waiting workers when a job arrives" in new BaseScope {
      val gen1 = generator(job1, 2)
      subject ! WorkerReady
      subject ! WorkerReady
      subject ! AddWorkGenerator(gen1)
      expectMsg(HandleUpload(job1, gen1.uploads(0)))
      expectMsg(HandleUpload(job1, gen1.uploads(1)))
    }

    "give work messages to two workers for the same job" in new BaseScope {
      val gen1 = generator(job1, 2)
      subject ! AddWorkGenerator(gen1)
      subject ! WorkerReady
      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))
      expectMsg(HandleUpload(job1, gen1.uploads(1)))
    }

    "handle work in round-robin fashion" in new BaseScope {
      val gen1 = generator(job1, 2)
      val gen2 = generator(job2, 2)
      val gen3 = generator(job3, 2)
      subject ! AddWorkGenerator(gen1)
      subject ! AddWorkGenerator(gen2)
      subject ! AddWorkGenerator(gen3)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))

      subject ! WorkerReady
      expectMsg(HandleUpload(job2, gen2.uploads(0)))

      subject ! WorkerReady
      expectMsg(HandleUpload(job3, gen3.uploads(0)))

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(1)))
    }

    "skip a generator when it has no work" in new BaseScope {
      val gen1 = generator(job1, 1) // second call to .nextWork will return NoWorkForNow
      val gen2 = generator(job2, 2)
      val gen3 = generator(job3, 2)
      subject ! AddWorkGenerator(gen1)
      subject ! AddWorkGenerator(gen2)
      subject ! AddWorkGenerator(gen3)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))

      subject ! WorkerReady
      expectMsg(HandleUpload(job2, gen2.uploads(0)))

      subject ! WorkerReady
      expectMsg(HandleUpload(job3, gen3.uploads(0)))

      subject ! WorkerReady
      expectMsg(HandleUpload(job2, gen2.uploads(1)))
    }

    "resume a work generator when it gets work and a worker is waiting" in new BaseScope {
      val gen1 = generator(job1, 1)
      subject ! AddWorkGenerator(gen1)

      subject ! WorkerReady
      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))

      subject ! WorkerDoneHandleUpload(job1)
      expectMsg(FinishJob(job1))
    }

    "resume a work generator when it gets finished and then a worker arrives" in new BaseScope {
      val gen1 = generator(job1, 1)
      subject ! AddWorkGenerator(gen1)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))
      subject ! WorkerDoneHandleUpload(job1)

      subject ! WorkerReady
      expectMsg(FinishJob(job1))
    }

    "cancel jobs" in new BaseScope {
      val gen1 = generator(job1, 5)
      subject ! AddWorkGenerator(gen1)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))

      subject ! CancelJob(job1.documentSetCreationJobId)
      expectMsg(CancelHandleUpload(job1))
    }

    "not crash when cancelling a job that is already finished" in new BaseScope {
      val gen1 = generator(job1, 0)
      subject ! AddWorkGenerator(gen1)

      subject ! WorkerReady
      subject ! CancelJob(job1.documentSetCreationJobId)

      expectMsg(FinishJob(job1))

      subject ! WorkerReady
      expectNoMsg(Duration.Zero)
    }

    "still send FinishJob after CancelHandleUpload" in new BaseScope {
      val gen1 = generator(job1, 5)
      subject ! AddWorkGenerator(gen1)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))

      subject ! CancelJob(job1.documentSetCreationJobId)
      expectMsg(CancelHandleUpload(job1))

      subject ! WorkerDoneHandleUpload(job1)
      subject ! WorkerReady

      expectMsg(FinishJob(job1))
    }

    "wait on FinishJob until all uploads are finished" in new BaseScope {
      val gen1 = generator(job1, 5)
      subject ! AddWorkGenerator(gen1)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))

      subject ! CancelJob(job1.documentSetCreationJobId)
      expectMsg(CancelHandleUpload(job1))

      subject ! WorkerReady
      expectNoMsg(Duration.Zero)

      subject ! WorkerDoneHandleUpload(job1)
      expectMsg(FinishJob(job1))
    }

    "only cancel jobs for workers that are processing them" in new BaseScope {
      // In other words: if a worker has never seen a job, don't message it.
      val gen1 = generator(job1, 5)
      val gen2 = generator(job2, 5)
      subject ! AddWorkGenerator(gen1)
      subject ! AddWorkGenerator(gen2)

      val worker1 = TestProbe()
      val worker2 = TestProbe()

      subject.tell(WorkerReady, worker1.ref) // worker1: job1
      subject.tell(WorkerReady, worker2.ref) // worker2: job2

      worker1.expectMsg(HandleUpload(job1, gen1.uploads(0)))
      worker2.expectMsg(HandleUpload(job2, gen2.uploads(0)))

      subject ! CancelJob(job1.documentSetCreationJobId)

      worker1.expectMsg(CancelHandleUpload(job1))
      worker2.expectNoMsg(Duration.Zero)
    }

    "not cancel jobs on a worker that has said it is not working on that job" in new BaseScope {
      // In other words: after a worker has said it is done with a job, don't message it.
      val gen1 = generator(job1, 5)
      val gen2 = generator(job2, 5)
      subject ! AddWorkGenerator(gen1)
      subject ! AddWorkGenerator(gen2)

      subject ! WorkerReady
      expectMsg(HandleUpload(job1, gen1.uploads(0)))
      subject ! WorkerDoneHandleUpload(job1)
      subject ! WorkerReady
      expectMsg(HandleUpload(job2, gen2.uploads(0)))

      subject ! CancelJob(job1.documentSetCreationJobId)
      expectNoMsg(Duration.Zero)

      subject ! WorkerDoneHandleUpload(job2)
      subject ! WorkerReady
      expectMsg(FinishJob(job1))

      subject ! WorkerReady
      expectMsg(HandleUpload(job2, gen2.uploads(1)))
    }
  }
}
