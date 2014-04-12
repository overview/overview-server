package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol._
import scala.concurrent.duration._
import scala.language.postfixOps

trait FileGroupTaskWorker extends Actor {
  import context._
  
  protected val jobQueuePath: String

  private val JobQueueId: String = "Job Queue"
  private val RetryInterval: FiniteDuration = 1 second
  
  private val jobQueueSelection = context.actorSelection(jobQueuePath)
  private var jobQueue: ActorRef = _
  
  lookForJobQueue

  def receive = {
    case ActorIdentity(JobQueueId, Some(jq)) => { 
      jobQueue = jq
      jobQueue ! RegisterWorker(self)
    }
    case ActorIdentity(JobQueueId, None) => 
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }
  }
  
  private def lookForJobQueue = jobQueueSelection ! Identify(JobQueueId)
}