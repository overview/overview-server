package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.TestActor
import akka.testkit.TestActorRef
import akka.testkit.TestProbe

import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol.RemoveFiles
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol.RemoveFileGroup
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess
import org.overviewproject.jobhandler.filegroup.task.step.FinalStep
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.test.ForwardingActor
import org.overviewproject.test.ParameterStore
import org.specs2.mock.Mockito
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

    "write document set info when completing a document set" in new RunningTaskWorkerContext {
      createJobQueue.handingOutTask(
        CompleteDocumentSet(documentSetId, fileGroupId))

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask

      updateDocumentSetInfoWasCalled(documentSetId)
    }

    "create index for document set" in new WorkingSearchIndexContext {
      createJobQueue.handingOutTask {
        CreateSearchIndexAlias(documentSetId, fileGroupId)
      }

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask
    }

    "cancel a job" in new CancellableProcessContext {
      createJobQueue.handingOutTask(
        CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref))

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)

      worker ! CancelTask

      step.success(())

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))
    }

    "ignore TaskAvailable when not ready" in new CancellableProcessContext {
      createWorker

      createJobQueue.handingOutTask(
        CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref))

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)

      worker ! TaskAvailable
      worker ! CancelTask

      step.success(())

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

    }

    "delete a file upload job" in new RunningTaskWorkerContext {
      createWorker

      createJobQueue.handingOutTask(DeleteFileUploadJob(documentSetId, fileGroupId))

      jobQueueProbe.expectInitialReadyForTask

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))
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

      val uploadedFileProcessCreator = smartMock[UploadedFileProcessCreator]
      val searchIndex = smartMock[ElasticSearchIndexClient]
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
        val uploadedFileProcess = smartMock[UploadedFileProcess]

        uploadedFileProcessCreator.create(any, any, any, any) returns uploadedFileProcess

        uploadedFileProcess.start(any) returns firstStep.execute
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
          uploadedFileProcessCreator, searchIndex, uploadedFile, fileId))
      }

      protected def createPagesTaskStepsWereExecuted =
        worker.underlyingActor.executeFn.wasCalledNTimes(2)

      protected def deleteFileUploadJobWasCalled(documentSetId: Long, fileGroupId: Long) =
        worker.underlyingActor.deleteFileUploadJobFn.wasCalledWith((documentSetId, fileGroupId))

      protected def updateDocumentSetInfoWasCalled(documentSetId: Long) =
        worker.underlyingActor.updateDocumentSetInfoFn.wasCalledWith(documentSetId)

    }

    trait WorkingSearchIndexContext extends RunningTaskWorkerContext {
      searchIndex.addDocumentSet(documentSetId) returns Future.successful(())
    }

    trait MultistepProcessContext extends RunningTaskWorkerContext {
      val options = UploadProcessOptions("en", false)

      class SimpleTaskStep(n: Int) extends TaskStep {
        override def execute: Future[TaskStep] = {
          val nextStep = if (n == 0) FinalStep else new SimpleTaskStep(n - 1)
          Future.successful(nextStep)
        }
      }

      val message = "failure"

      class FailingStep extends TaskStep {
        override def execute: Future[TaskStep] = Future {
          throw new Exception(message)
        }
      }

      override def firstStep: TaskStep = new SimpleTaskStep(1)
    }

    trait FailingProcessContext extends MultistepProcessContext {

      override def firstStep: TaskStep = new FailingStep

      def writeDocumentProcessingErrorWasCalled(documentSetId: Long, filename: String, message: String) =
        worker.underlyingActor.writeDocumentProcessingErrorFn.wasCalledWith(documentSetId, filename, message)

    }

    trait CancellableProcessContext extends MultistepProcessContext {
      val step = Promise[Unit]()

      override def firstStep: TaskStep = new WaitingStep

      class WaitingStep extends TaskStep {
        override def execute: Future[TaskStep] =
          step.future.map { _ =>
            new FailingStep
          }
      }
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
