package com.overviewdocs.jobhandler.filegroup.task


import akka.actor.Scheduler
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.TimeoutException
import akka.dispatch.OnFailure

class TimeoutGenerator(scheduler: Scheduler, implicit val executionContext: ExecutionContext) {
  
  def runWithTimeout[T](timeout: FiniteDuration, onTimeout: => Unit, slowCommand: Future[T]): Future[T] = {

    val timeoutFailure = akka.pattern.after(timeout, scheduler)(Future.failed(new TimeoutException))
    val f = Future.firstCompletedOf(Seq(timeoutFailure, slowCommand))
    
    f.onFailure {
      case e: TimeoutException => onTimeout
      case _ => 
    }
    
    f
  }

}