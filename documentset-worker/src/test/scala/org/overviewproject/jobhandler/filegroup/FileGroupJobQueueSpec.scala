package org.overviewproject.jobhandler.filegroup

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import akka.testkit._
import org.specs2.time.NoTimeConversions
import akka.actor.ActorRef

class TestFileGroupJobQueue(jobs: Seq[Long]) extends FileGroupJobQueue {

  class TestStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Iterable[Long] = jobs
  }

  override protected val storage = new TestStorage
}

class FileGroupJobQueueSpec extends Specification with NoTimeConversions {

  "FileGroupJobQueue" should {

    abstract class JobQueueContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val fileGroupId = 2l

      protected var fileGroupJobQueue: TestActorRef[TestFileGroupJobQueue] = _
      protected var worker: TestProbe = _

      def before = {
        fileGroupJobQueue = TestActorRef(new TestFileGroupJobQueue(preloadedJobs))
        worker = TestProbe()
      }

      protected def preloadedJobs: Seq[Long] = Seq.empty
    }

    abstract class PreloadedJobQueueContext extends JobQueueContext {
      protected val numberOfUploadedFiles = 10
      protected val uploadedFileIds: Seq[Long] = Seq.tabulate(numberOfUploadedFiles)(_.toLong)

      override def preloadedJobs: Seq[Long] = uploadedFileIds

      protected def createNWorkers(numberOfWorkers: Int): Seq[TestProbe] = Seq.fill(numberOfWorkers)(TestProbe())

    }

    "notify registered workers when tasks becomes available" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)

      worker.expectMsg(TaskAvailable)
    }

    "send available tasks to workers that ask for them" in new PreloadedJobQueueContext {
      val workers = createNWorkers(numberOfUploadedFiles)
      workers.foreach(w => fileGroupJobQueue ! RegisterWorker(w.ref))

      fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)

      for ((w, f) <- workers.zip(uploadedFileIds)) yield {
        w.expectMsg(TaskAvailable)
        w.reply(ReadyForTask)

        w.expectMsg(Task(documentSetId, fileGroupId, f))

      }

    }

    "notify worker if tasks are available when it registers" in new PreloadedJobQueueContext {
      fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      worker.expectMsg(TaskAvailable)
    }

    "don't send anything if no tasks are available" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      fileGroupJobQueue.tell(ReadyForTask, worker.ref)

      worker.expectNoMsg(500 millis)
    }

    "notify requester when all tasks for a fileGroupId are complete" in new PreloadedJobQueueContext {
      worker.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
          msg match {
            case TaskAvailable => sender.tell(ReadyForTask, worker.ref)
            case Task(ds, fg, uf) => { 
              sender.tell(TaskDone(fg, uf), worker.ref)
              sender.tell(ReadyForTask, worker.ref)
            }
          }
          TestActor.KeepRunning
        }
      })

      fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      expectMsg(FileGroupDocumentsCreated(documentSetId))
    }


    "handle cancellations" in {
      todo
    }

  }
}