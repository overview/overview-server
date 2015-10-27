package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.TestActor
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

import com.overviewdocs.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import com.overviewdocs.searchindex.ElasticSearchIndexClient
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.ForwardingActor

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

    "step through selected process" in new RunningTaskWorkerContext {
      createJobQueue.handingOutTask(
        CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref)
      )

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)
      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask

      processUploadedFileWasCalled(documentSetId, uploadedFileId, options, documentIdSupplier.ref)
    }

    "treat failing step as completed step" in new FailingProcessContext {
      createJobQueue.handingOutTask(
        CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref)
      )

      createWorker

      jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
      jobQueueProbe.expectMsg(ReadyForTask)

      jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

      jobQueueProbe.expectReadyForTask
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

    //"cancel a job" in new CancellableProcessContext {
    //  createJobQueue.handingOutTask(
    //    CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref))

    //  createWorker

    //  jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
    //  jobQueueProbe.expectMsg(ReadyForTask)

    //  worker ! CancelTask

    //  step.success(())

    //  jobQueueProbe.expectMsg(TaskDone(documentSetId, None))
    //}

    //"ignore TaskAvailable when not ready" in new CancellableProcessContext {
    //  createWorker

    //  createJobQueue.handingOutTask(
    //    CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref))

    //  jobQueueProbe.expectMsgClass(classOf[RegisterWorker])
    //  jobQueueProbe.expectMsg(ReadyForTask)

    //  worker ! TaskAvailable
    //  worker ! CancelTask

    //  step.success(())

    //  jobQueueProbe.expectMsg(TaskDone(documentSetId, None))

    //}

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
      protected val documentSetId: Long = 1L
      protected val fileGroupId: Long = 2L
      protected val uploadedFileId: Long = 10L
      protected val fileId: Long = 20L
      protected val filename: String = "filename"
      protected val options = UploadProcessOptions("en", false)

      var jobQueue: ActorRef = _
      var jobQueueProbe: JobQueueTestProbe = _

      val JobQueueName = "jobQueue"
      val JobQueuePath = s"/user/$JobQueueName"

      val searchIndex = smartMock[ElasticSearchIndexClient]
      val documentIdSupplier: TestProbe = new TestProbe(system)

      protected def createJobQueue: JobQueueTestProbe = {
        jobQueueProbe = new JobQueueTestProbe(system)
        jobQueue = system.actorOf(ForwardingActor(jobQueueProbe.ref), JobQueueName)

        jobQueueProbe
      }

      protected def firstStep: Future[Unit] = Future.successful(())
    }

    trait RunningTaskWorkerContext extends TaskWorkerContext {
      var worker: TestActorRef[TestFileGroupTaskWorker] = _

      protected def createWorker: Unit = {
        worker = TestActorRef(new TestFileGroupTaskWorker(JobQueuePath, searchIndex, fileId))
      }

      protected def updateDocumentSetInfoWasCalled(documentSetId: Long) =
        worker.underlyingActor.updateDocumentSetInfoFn.wasCalledWith(documentSetId)

      protected def processUploadedFileWasCalled(documentSetId: Long, uploadedFileId: Long,
                                                 options: UploadProcessOptions, documentIdSupplier: ActorRef) =
        worker.underlyingActor.processUploadedFileFn
          .wasCalledWith((documentSetId, uploadedFileId, options, documentIdSupplier))

    }

    trait WorkingSearchIndexContext extends RunningTaskWorkerContext {
      searchIndex.addDocumentSet(documentSetId) returns Future.successful(())
    }

    trait FailingProcessContext extends RunningTaskWorkerContext {
      val exception = new Exception("foo")
      override def firstStep = Future.failed(exception)
    }

    //trait CancellableProcessContext extends TaskWorkerContext {
    //  val step = Promise[Unit]()

    //  override def firstStep = step.future
    //}

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


