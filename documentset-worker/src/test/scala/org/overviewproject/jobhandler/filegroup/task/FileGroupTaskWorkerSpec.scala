package org.overviewproject.jobhandler.filegroup.task

import akka.actor._
import akka.testkit._
import scala.concurrent.duration._
import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol._
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.test.{ ActorSystemContext, ParameterStore, ForwardingActor }
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

class FileGroupTaskWorkerSpec extends Specification with NoTimeConversions {
  sequential

  "FileGroupTaskWorker" should {

    "register with job queue" in new RunningTaskWorkerContext {
      createJobQueue
      createWorker

      jobQueueProbe.expectMsg(RegisterWorker(worker))
    }

    "retry job queue registration on initial failure" in new RunningTaskWorkerContext {
      createWorker
      createJobQueue

      jobQueueProbe.expectMsg(RegisterWorker(worker))
    }

    "request task when available" in new RunningTaskWorkerContext {
      createWorker
      createJobQueue.withTaskAvailable

      jobQueueProbe.expectInitialReadyForTask
    }

    "step through task until done" in new RunningTaskWorkerContext {
      createJobQueue.handingOutTask(CreatePagesTask(documentSetId, fileGroupId, uploadedFileId))

      createWorker

      jobQueueProbe.expectTaskDone(documentSetId, fileGroupId, uploadedFileId, fileId)
      jobQueueProbe.expectReadyForTask

      createPagesTaskStepsWereExecuted
    }

    "step through CreateDocumentsTask" in new RunningTaskWorkerContext {
      createWorker
      createJobQueue.handingOutTask(CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments = true))

      jobQueueProbe.expectInitialReadyForTask
      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask

      createPagesTaskStepsWereExecuted

    }

    "cancel a job in progress" in new GatedTaskWorkerContext {
      import GatedTaskWorkerProtocol._

      createWorker
      createJobQueue.handingOutTask(CreatePagesTask(documentSetId, fileGroupId, uploadedFileId))

      jobQueueProbe.expectInitialReadyForTask

      worker ! CancelYourself
      worker ! CompleteTaskStep

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      taskWasCancelled
    }

    "ignore TaskAvailable message when not Ready" in new GatedTaskWorkerContext {
      import GatedTaskWorkerProtocol._

      createWorker
      createJobQueue.handingOutTask(CreatePagesTask(documentSetId, fileGroupId, uploadedFileId))

      jobQueueProbe.expectInitialReadyForTask

      worker ! TaskAvailable
      worker ! CancelYourself

      worker ! CompleteTaskStep

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

    }

    "delete a file upload job" in new RunningTaskWorkerContext {
      createWorker

      createJobQueue.handingOutTask(DeleteFileUploadJob(documentSetId, fileGroupId))

      jobQueueProbe.expectInitialReadyForTask

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))
    }

    "notify file removal queue after file upload job is deleted" in new RunningTaskWorkerContext {
      createWorker

      createJobQueue.handingOutTask(DeleteFileUploadJob(documentSetId, fileGroupId))

      jobQueueProbe.expectInitialReadyForTask

      fileRemovalQueueProbe.expectMsg(RemoveFiles)
    }

    "ignore CancelTask message if not working on a task" in new RunningTaskWorkerContext {
      createJobQueue
      createWorker

      jobQueueProbe.expectMsg(RegisterWorker(worker))

      worker ! CancelTask

      jobQueueProbe.expectNoMsg(50 millis)
    }

    trait TaskWorkerContext extends ActorSystemContext {
      protected val documentSetId: Long = 1l
      protected val fileGroupId: Long = 2l
      protected val uploadedFileId: Long = 10l
      protected val fileId: Long = 20l

      var jobQueue: ActorRef = _
      var jobQueueProbe: JobQueueTestProbe = _
      var progressReporter: ActorRef = _
      var progressReporterProbe: TestProbe = _
      var fileRemovalQueue: ActorRef = _
      var fileRemovalQueueProbe: TestProbe = _

      val JobQueueName = "jobQueue"
      val JobQueuePath = s"/user/$JobQueueName"

      val ProgressReporterName = "progressReporter"
      val ProgressReporterPath = s"/user/$ProgressReporterName"

      val FileRemovalQueueName = "fileRemovalQueue"
      val FileRemovalQueuePath = s"/user/$FileRemovalQueueName"

      protected def createProgressReporter: TestProbe = {
        progressReporterProbe = new TestProbe(system)
        progressReporter = system.actorOf(ForwardingActor(progressReporterProbe.ref), ProgressReporterName)

        progressReporterProbe
      }

      protected def createJobQueue: JobQueueTestProbe = {
        jobQueueProbe = new JobQueueTestProbe(system)
        jobQueue = system.actorOf(ForwardingActor(jobQueueProbe.ref), JobQueueName)

        jobQueueProbe
      }

      protected def createFileRemovalQueue: TestProbe = {
        fileRemovalQueueProbe = new TestProbe(system)
        fileRemovalQueue = system.actorOf(ForwardingActor(fileRemovalQueueProbe.ref), FileRemovalQueueName)
        
        fileRemovalQueueProbe
      }
    }

    trait RunningTaskWorkerContext extends TaskWorkerContext {

      var worker: TestActorRef[TestFileGroupTaskWorker] = _

      protected def createWorker: Unit = {
        createFileRemovalQueue
        createProgressReporter
        worker =
          TestActorRef(new TestFileGroupTaskWorker(JobQueuePath, ProgressReporterPath, FileRemovalQueuePath, fileId))
      }

      protected def createPagesTaskStepsWereExecuted =
        worker.underlyingActor.executeFn.wasCalledNTimes(2)

      protected def deleteFileUploadJobWasCalled(documentSetId: Long, fileGroupId: Long) =
        worker.underlyingActor.deleteFileUploadJobFn.wasCalledWith((documentSetId, fileGroupId))

    }

    trait GatedTaskWorkerContext extends TaskWorkerContext {
      var worker: ActorRef = _
      private val cancelFn = ParameterStore[Unit]

      protected def createWorker: Unit = {
        createFileRemovalQueue
        createProgressReporter
        worker = 
          system.actorOf(Props(new GatedTaskWorker(JobQueuePath, ProgressReporterPath, FileRemovalQueuePath, cancelFn)))
      }

      protected def taskWasCancelled = cancelFn.wasCalledNTimes(1)
    }

    class JobQueueTestProbe(actorSystem: ActorSystem) extends TestProbe(actorSystem) {

      def expectInitialReadyForTask = {
        expectMsgClass(classOf[RegisterWorker])
        expectMsg(ReadyForTask)
      }

      def expectReadyForTask = expectMsg(ReadyForTask)

      def expectTaskDone(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long, outputFileId: Long) = {
        expectMsgClass(classOf[RegisterWorker])
        expectMsg(ReadyForTask)
        expectMsg(TaskDone(documentSetId, Some(outputFileId)))
      }

      def withTaskAvailable: JobQueueTestProbe = {
        this.setAutoPilot(new JobQueueWithTaskAvailable)
        this
      }

      def handingOutTask[A](task: A): JobQueueTestProbe = {
        this.setAutoPilot(new JobQueueHandingOutTask(task))
        this
      }
    }

    abstract class JobQueueAutoPilot extends TestActor.AutoPilot {
      protected def messageMatcherChain(): PartialFunction[Any, Any] = PartialFunction.empty

      private def ignoreMessage: PartialFunction[Any, Any] = {
        case _ =>
      }

      def run(sender: ActorRef, message: Any) = {
        messageMatcherChain().orElse(ignoreMessage)(message)
        TestActor.KeepRunning
      }
    }

    class JobQueueWithTaskAvailable extends JobQueueAutoPilot {
      override protected def messageMatcherChain(): PartialFunction[Any, Any] = {
        super.messageMatcherChain.orElse {
          case RegisterWorker(worker) => worker ! TaskAvailable
        }
      }

    }

    class JobQueueHandingOutTask[A](task: A) extends TestActor.AutoPilot {
      private var numberOfTasksToHandOut = 1

      def run(sender: ActorRef, message: Any): TestActor.AutoPilot = {
        message match {
          case RegisterWorker(worker) => worker ! TaskAvailable
          case ReadyForTask if numberOfTasksToHandOut > 0 => {
            sender ! task
            numberOfTasksToHandOut -= 1
          }
          case _ =>
        }
        TestActor.KeepRunning
      }
    }
  }
}
