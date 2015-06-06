package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import scala.collection.mutable

import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._

/**
 * Takes care of all the little tasks needed to complete a job. Keeps track of when the job
 * is complete and whether new tasks need to be created.
 */
trait JobShepherd {
  /** 
   * Create the tasks for the job 
   * 
   *  @returns the number of tasks created 
   */
  def createTasks: Int = {
    val tasks = generateTasks

    remainingTasks ++= tasks

    remainingTasks.size
  }

  /** Notify the shepherd that a task has been sent to a worker */
  def startTask(task: TaskWorkerTask): Unit = {
    remainingTasks -= task
    startedTasks += task
  }

  /** Notify the shepherd that a task is complete */
  def completeTask(task: TaskWorkerTask): Unit = startedTasks -= task
  
  /** Tell the shepherd to stop tracking not started tasks */
  def removeNotStartedTasks: Unit = {
    jobCancelled = true
    remainingTasks.clear()
  }

  /** @returns true if all tasks for the job have been completed */
  def allTasksComplete: Boolean = remainingTasks.isEmpty && startedTasks.isEmpty

  private val remainingTasks: mutable.Set[TaskWorkerTask] = mutable.Set.empty
  private val startedTasks: mutable.Set[TaskWorkerTask] = mutable.Set.empty

  /** Create job specific tasks */
  protected def generateTasks: Iterable[TaskWorkerTask]
  
  /** Add more tasks */
  protected def addTask(task: TaskWorkerTask): Unit = remainingTasks += task

  /** `true` if the job has been cancelled */
  protected var jobCancelled: Boolean = false
}
