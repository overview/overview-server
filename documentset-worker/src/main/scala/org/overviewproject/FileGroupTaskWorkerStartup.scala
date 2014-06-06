package org.overviewproject

import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorker
import org.overviewproject.util.Logger
import scala.concurrent.duration.Duration

object FileGroupTaskWorkerStartup {

  def apply(fileGroupJobQueuePath: String)(implicit system: ActorSystem): Unit = {
    
    system.actorOf(TaskWorkerSupervisor(fileGroupJobQueuePath), "TaskWorkerSupervisor")
  }

}

class TaskWorkerSupervisor(jobQueuePath: String) extends Actor {

  private val NumberOfTaskWorkers = 2
  private val taskWorkers: Seq[ActorRef] = Seq.tabulate(NumberOfTaskWorkers)(n =>
    context.actorOf(FileGroupTaskWorker(jobQueuePath), workerName(n)))

  private def workerName(n: Int): String = s"TaskWorker-$n"

  override def preStart = taskWorkers.foreach(context.watch) 
    
  override def supervisorStrategy = OneForOneStrategy(0, Duration.Inf) {
    case _: Exception => Restart
    case _ => Escalate
  }

  def receive = {
    case Terminated(a) =>
      Logger.error(s"Task Worker ${a.path} died unexpectedly. Restarting.")
  }
}

object TaskWorkerSupervisor {
  def apply(jobQueuePath: String): Props = Props(new TaskWorkerSupervisor(jobQueuePath))
}