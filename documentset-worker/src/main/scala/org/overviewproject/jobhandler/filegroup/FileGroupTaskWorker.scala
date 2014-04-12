package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol._
import scala.concurrent.duration._
import scala.language.postfixOps

trait FileGroupTaskWorker extends Actor {
  import context._
  
  protected val jobQueuePath: String

  private val jobQueueSelection = context.actorSelection(jobQueuePath)
  private var jobQueue: ActorRef = _
  
  lookForJobQueue

  def receive = {
    case ActorIdentity(_, Some(jq)) => { 
      jobQueue = jq
      jobQueue ! RegisterWorker(self)
    }
    case ActorIdentity(_, None) => 
      system.scheduler.scheduleOnce(1000 millis) { lookForJobQueue }
  }
  
  private def lookForJobQueue = jobQueueSelection ! Identify(None)
}