package org.overviewproject

import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorker
import org.overviewproject.util.Logger
import scala.concurrent.duration.Duration
import org.overviewproject.jobhandler.filegroup.task.TempDirectory

object FileGroupTaskWorkerStartup {

  def apply(fileGroupJobQueuePath: String)(implicit context: ActorContext): Props = {
    TempDirectory.create
    TaskWorkerSupervisor(fileGroupJobQueuePath)
  }

}

class TaskWorkerSupervisor(jobQueuePath: String) extends Actor {

  private val NumberOfTaskWorkers = 2
  private val taskWorkers: Seq[ActorRef] = Seq.tabulate(NumberOfTaskWorkers)(n =>
    context.actorOf(FileGroupTaskWorker(jobQueuePath), workerName(n)))

  private def workerName(n: Int): String = s"TaskWorker-$n"

  override def preStart = taskWorkers.foreach(context.watch) 
    
  // If an error escapes out of the workers, it is sufficiently serious 
  // that the JVM should be shut down
  override def supervisorStrategy = OneForOneStrategy(-1, Duration.Inf) {
    case _ => Escalate
  }

  def receive = {
    case Terminated(a) =>
      Logger.error(s"Task Worker ${a.path} died unexpectedly.")
  }
}

object TaskWorkerSupervisor {
  def apply(jobQueuePath: String): Props = Props(new TaskWorkerSupervisor(jobQueuePath))
}