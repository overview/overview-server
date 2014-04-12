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
import akka.actor.ActorSystem

class FileGroupJobQueueSpec extends Specification with NoTimeConversions {

  "FileGroupJobQueue" should {

    "notify registered workers when tasks becomes available" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      submitJob

      worker.expectMsg(TaskAvailable)
    }

    "send available tasks to workers that ask for them" in new JobQueueContext {
      val workers = createNWorkers(numberOfUploadedFiles)
      workers.foreach(w => fileGroupJobQueue ! RegisterWorker(w.ref))

      submitJob
      val receivedTasks = expectTasks(workers)
      mustMatchUploadedFileIds(receivedTasks, uploadedFileIds)
    }

    "notify worker if tasks are available when it registers" in new JobQueueContext {
      submitJob
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      worker.expectMsg(TaskAvailable)
    }

    "don't send anything if no tasks are available" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      fileGroupJobQueue.tell(ReadyForTask, worker.ref)

      worker.expectNoMsg(500 millis)
    }

    "notify requester when all tasks for a fileGroupId are complete" in new JobQueueContext {
      ActAsImmediateJobCompleter(worker)
      submitJob

      fileGroupJobQueue ! RegisterWorker(worker.ref)

      expectMsg(FileGroupDocumentsCreated(documentSetId))
    }

    "handle cancellations" in {
      todo
    }

    abstract class JobQueueContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val fileGroupId = 2l
      protected val numberOfUploadedFiles = 10
      protected val uploadedFileIds: Seq[Long] = Seq.tabulate(numberOfUploadedFiles)(_.toLong)

      protected var fileGroupJobQueue: TestActorRef[TestFileGroupJobQueue] = _
      protected var worker: TestProbe = _

      def before = {
        val x = system
        fileGroupJobQueue = TestActorRef(new TestFileGroupJobQueue(uploadedFileIds))
        worker = TestProbe()
      }

      protected def createNWorkers(numberOfWorkers: Int): Seq[WorkerTestProbe] =
        Seq.fill(numberOfWorkers)(new WorkerTestProbe(documentSetId, fileGroupId, system))

      protected def submitJob =
        fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)

      protected def expectTasks(workers: Seq[WorkerTestProbe]) = workers.map { _.expectATask }
      
      protected def mustMatchUploadedFileIds(tasks: Seq[Task], uploadedFileIds: Seq[Long]) =
    		  tasks.map(_.uploadedFileId) must containTheSameElementsAs(uploadedFileIds)        
    }

    class WorkerTestProbe(documentSetId: Long, fileGroupId: Long, actorSystem: ActorSystem)
        extends TestProbe(actorSystem) {
      def expectATask = {
        expectMsg(TaskAvailable)
        reply(ReadyForTask)

        expectMsgClass(classOf[Task])
      }
    }

    class ImmediateJobCompleter(worker: ActorRef) extends TestActor.AutoPilot {
      def run(sender: ActorRef, message: Any): TestActor.AutoPilot = {
        message match {
          case TaskAvailable => sender.tell(ReadyForTask, worker)
          case Task(ds, fg, uf) => {
            sender.tell(TaskDone(fg, uf), worker)
            sender.tell(ReadyForTask, worker)
          }
        }
        TestActor.KeepRunning
      }
    }

    object ActAsImmediateJobCompleter {
      def apply(probe: TestProbe): TestProbe = {
        probe.setAutoPilot(new ImmediateJobCompleter(probe.ref))
        probe
      }
    }
  }
}