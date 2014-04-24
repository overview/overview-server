package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol._
import akka.actor.ActorRef
import akka.actor.Props
import org.overviewproject.test.ForwardingActor
import akka.testkit.TestActorRef
import akka.agent.Agent
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.testkit.TestActor
import akka.actor.ActorSystem


class FileGroupTaskWorkerSpec extends Specification {

  "FileGroupJobQueue" should {

    "register with job queue" in new TaskWorkerContext {
      createJobQueue
      createWorker

      jobQueueProbe.expectMsg(RegisterWorker(worker))
    }

    "retry job queue registration on initial failure" in new TaskWorkerContext {
      createWorker
      createJobQueue

      jobQueueProbe.expectMsg(RegisterWorker(worker))
    }

    "request task when available" in new TaskWorkerContext {
      createJobQueue.withTaskAvailable

      createWorker

      jobQueueProbe.expectInitialReadyForTask
    }

    "step through task until done" in new TaskWorkerContext {
      createJobQueue.handingOutTask(CreatePagesTask(fileGroupId, uploadedFileId))
      
      createWorker

      jobQueueProbe.expectTaskDone(fileGroupId, uploadedFileId)
      jobQueueProbe.expectReadyForTask
      
      taskStepsWereExecuted
    }

    "create page views" in {
      todo
    }

    "create document" in {
      todo
    }

    "reconnect to job queue on failure" in {
      todo
    }

    "cancel a job in progress" in {
      todo
    }

    abstract class TaskWorkerContext extends ActorSystemContext with Before {
      protected val documentSetId: Long = 1l
      protected val fileGroupId: Long = 2l
      protected val uploadedFileId: Long = 10l

      var worker: TestActorRef[TestFileGroupTaskWorker] = _
      var jobQueue: ActorRef = _
      var jobQueueProbe: JobQueueTestProbe = _

      val JobQueueName = "jobQueue"
      val JobQueuePath: String = s"/user/$JobQueueName"

      def before = {} //necessary or tests can't create actors for some reason

      protected def createJobQueue: JobQueueTestProbe = {
        jobQueueProbe = new JobQueueTestProbe(system)
        jobQueue = system.actorOf(ForwardingActor(jobQueueProbe.ref), JobQueueName)

        jobQueueProbe
      }

      protected def createWorker: Unit = worker = TestActorRef(new TestFileGroupTaskWorker(JobQueuePath))

      protected def taskStepsWereExecuted = {
        val pendingCalls = worker.underlyingActor.startTaskCallsInProgress
        awaitCond(pendingCalls.isCompleted)
        worker.underlyingActor.numberOfStartTaskCalls must be equalTo (2)
      }

    }

    class JobQueueTestProbe(actorSystem: ActorSystem) extends TestProbe(actorSystem) {

      def expectInitialReadyForTask = {
        expectMsgClass(classOf[org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol.RegisterWorker])
        expectMsg(ReadyForTask)
      }
      
      def expectReadyForTask = expectMsg(ReadyForTask)

      def expectTaskDone(fileGroupId: Long, uploadedFileId: Long) = {
        expectMsgClass(classOf[RegisterWorker])
        expectMsg(ReadyForTask)
        expectMsg(TaskDone(fileGroupId, uploadedFileId))
      }

      def withTaskAvailable: JobQueueTestProbe = {
        this.setAutoPilot(new JobQueueWithTaskAvailable)
        this
      }

      def handingOutTask(task: CreatePagesTask): JobQueueTestProbe = {
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

    class JobQueueHandingOutTask(task: CreatePagesTask) extends TestActor.AutoPilot {
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