package com.overviewdocs

import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration.Duration

import com.overviewdocs.jobhandler.filegroup.task.FileGroupTaskWorker
import com.overviewdocs.jobhandler.filegroup.task.TempDirectory
import com.overviewdocs.util.Logger

object FileGroupTaskWorkerStartup {

  def apply(
    fileGroupJobQueuePath: String,
    fileRemovalQueuePath: String,
    fileGroupRemovalQueuePath: String
  )(implicit context: ActorContext): Props = {
    TempDirectory.create
    TaskWorkerSupervisor(fileGroupJobQueuePath, fileRemovalQueuePath, fileGroupRemovalQueuePath)
  }

}

class TaskWorkerSupervisor(
  jobQueuePath: String,
  fileRemovalQueuePath: String,
  fileGroupRemovalQueuePath: String
) extends Actor {
  private val logger = Logger.forClass(getClass)

  private val NumberOfTaskWorkers = 2
  private val taskWorkers: Seq[ActorRef] = Seq.tabulate(NumberOfTaskWorkers) { n =>
    context.actorOf(
      FileGroupTaskWorker(jobQueuePath, fileRemovalQueuePath, fileGroupRemovalQueuePath),
      workerName(n)
    )
  }

  private def workerName(n: Int): String = s"TaskWorker-$n"

  override def preStart = taskWorkers.foreach(context.watch)

  // If an error escapes out of the workers, it is sufficiently serious 
  // that the JVM should be shut down
  override def supervisorStrategy = OneForOneStrategy(-1, Duration.Inf) {
    case _ => Escalate
  }

  def receive = {
    case Terminated(a) => logger.error("Task worker at path {} died unexpectedly", a.path)
  }
}

object TaskWorkerSupervisor {
  def apply(jobQueuePath: String, fileRemovalQueuePath: String, fileGroupRemovalQueuePath: String): Props = {
    Props(new TaskWorkerSupervisor(jobQueuePath, fileRemovalQueuePath, fileGroupRemovalQueuePath))
  }
}
