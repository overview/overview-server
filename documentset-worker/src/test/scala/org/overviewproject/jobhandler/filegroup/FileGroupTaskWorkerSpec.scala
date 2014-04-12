package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol.RegisterWorker
import akka.actor.ActorRef
import akka.actor.Props
import org.overviewproject.test.ForwardingActor

class TestFileGroupTaskWorker(override protected val jobQueuePath: String) extends FileGroupTaskWorker {

}

object TestFileGroupTaskWorker {
  def apply(jobQueuePath: String): Props = Props(new TestFileGroupTaskWorker(jobQueuePath))
}

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
      var worker: ActorRef = _
      var jobQueue: ActorRef = _
      var jobQueueProbe: TestProbe = _

      val JobQueueName = "jobQueue"
      val JobQueuePath: String = s"/user/$JobQueueName"

      def before = {} //necessary or tests can't create actors for some reason

      protected def createJobQueue: Unit = {
        jobQueueProbe = TestProbe()
        jobQueue = system.actorOf(ForwardingActor(jobQueueProbe.ref), JobQueueName)
      }

      protected def createWorker: Unit = worker = system.actorOf(TestFileGroupTaskWorker(JobQueuePath))
    }

  }
}