package org.overviewproject.jobhandler.filegroup.task

import akka.actor._
import akka.testkit._
import scala.concurrent.duration._
import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol._
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.test.{ ActorSystemContext, ParameterStore, ForwardingActor }
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess
import org.overviewproject.jobhandler.filegroup.task.process.StepGenerator
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobhandler.filegroup.task.step.FinalStep
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcessCreator
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import scala.concurrent.Future


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

    "step through selected process" in new MultistepProcessContext {
      createJobQueue.handingOutTask(
        CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref))

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)
      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask
    }


    "write DocumentProcessingError when step fails" in new FailingProcessContext {
      createJobQueue.handingOutTask(
        CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref))

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask
      
      writeDocumentProcessingErrorWasCalled(documentSetId, filename, message)
      
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

    "notify file group removal queue after file upload job is deleted" in new RunningTaskWorkerContext {
      createWorker

      createJobQueue.handingOutTask(DeleteFileUploadJob(documentSetId, fileGroupId))

      jobQueueProbe.expectInitialReadyForTask

      fileGroupRemovalQueueProbe.expectMsg(RemoveFileGroup(fileGroupId))
    }

    "ignore CancelTask message if not working on a task" in new RunningTaskWorkerContext {
      createJobQueue
      createWorker

      jobQueueProbe.expectMsg(RegisterWorker(worker))

      worker ! CancelTask

      jobQueueProbe.expectNoMsg(50 millis)
    }

    trait TaskWorkerContext extends ActorSystemContext with Mockito {
      protected val documentSetId: Long = 1l
      protected val fileGroupId: Long = 2l
      protected val uploadedFileId: Long = 10l
      protected val fileId: Long = 20l
      protected val filename: String = "filename"
      
      var jobQueue: ActorRef = _
      var jobQueueProbe: JobQueueTestProbe = _
      var progressReporter: ActorRef = _
      var progressReporterProbe: TestProbe = _
      var fileRemovalQueue: ActorRef = _
      var fileRemovalQueueProbe: TestProbe = _
      var fileGroupRemovalQueue: ActorRef = _
      var fileGroupRemovalQueueProbe: TestProbe = _

      val JobQueueName = "jobQueue"
      val JobQueuePath = s"/user/$JobQueueName"

      val ProgressReporterName = "progressReporter"
      val ProgressReporterPath = s"/user/$ProgressReporterName"

      val FileRemovalQueueName = "fileRemovalQueue"
      val FileRemovalQueuePath = s"/user/$FileRemovalQueueName"

      val FileGroupRemovalQueueName = "fileGroupRemovalQueue"
      val FileGroupRemovalQueuePath = s"/user/$FileGroupRemovalQueueName"

      val uploadedFileProcessSelector = smartMock[UploadProcessSelector]
      val documentIdSupplier: TestProbe = new TestProbe(system)

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

      protected def createFileGroupRemovalQueue: TestProbe = {
        fileGroupRemovalQueueProbe = new TestProbe(system)
        fileGroupRemovalQueue = system.actorOf(ForwardingActor(fileGroupRemovalQueueProbe.ref), FileGroupRemovalQueueName)

        fileGroupRemovalQueueProbe
      }

      protected def setupProcessSelection = {
        val uploadedFileProcessCreator = smartMock[UploadedFileProcessCreator]
        val uploadedFileProcess = smartMock[UploadedFileProcess]

        uploadedFileProcessSelector.select(any, any) returns uploadedFileProcessCreator
        uploadedFileProcessCreator.create(any, any) returns uploadedFileProcess
        uploadedFileProcess.start(any) returns firstStep
      }

      protected def firstStep: TaskStep = FinalStep

      protected def uploadedFile: Option[GroupedFileUpload] = {
        val f = smartMock[GroupedFileUpload]
        f.name returns filename
        
        Some(f)
      }

    }

    trait RunningTaskWorkerContext extends TaskWorkerContext {

      var worker: TestActorRef[TestFileGroupTaskWorker] = _

      protected def createWorker: Unit = {
        createFileGroupRemovalQueue
        createFileRemovalQueue
        createProgressReporter
        setupProcessSelection

        worker = TestActorRef(new TestFileGroupTaskWorker(
          JobQueuePath, ProgressReporterPath, FileRemovalQueuePath, FileGroupRemovalQueuePath,
          uploadedFileProcessSelector, uploadedFile, fileId))
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
        setupProcessSelection

        worker =
          system.actorOf(Props(new GatedTaskWorker(
            JobQueuePath, ProgressReporterPath, FileRemovalQueuePath, FileGroupRemovalQueuePath,
            uploadedFileProcessSelector, uploadedFile, cancelFn)))
      }

      protected def taskWasCancelled = cancelFn.wasCalledNTimes(1)
    }

    trait MultistepProcessContext extends RunningTaskWorkerContext {
      val options = UploadProcessOptions("en", false)

      class SimpleTaskStep(n: Int) extends TaskStep {
        override def execute: Future[TaskStep] = {
          val nextStep = if (n == 0) FinalStep else new SimpleTaskStep(n - 1)
          Future.successful(nextStep)
        }
      }

      override def firstStep: TaskStep = new SimpleTaskStep(1)
    }


    trait FailingProcessContext extends MultistepProcessContext {
      val message = "failure"
      class FailingStep extends TaskStep {
        override def execute: Future[TaskStep] = Future {
          throw new Exception(message)
        }
      }

      override def firstStep: TaskStep = new FailingStep
      
      def writeDocumentProcessingErrorWasCalled(documentSetId: Long, filename: String, message: String) = 
        worker.underlyingActor.writeDocumentProcessingErrorFn.wasCalledWith(documentSetId, filename, message)

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
