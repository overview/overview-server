package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._

trait JobShepherd {
  def createTasks: Int = {
    val tasks = generateTasks

    remainingTasks ++= tasks

    remainingTasks.size
  }

  def startTask(task: TaskWorkerTask): Unit = {
    remainingTasks -= task
    startedTasks += task
  }

  def completeTask(task: TaskWorkerTask): Unit = startedTasks -= task
  def removeNotStartedTasks: Unit = remainingTasks.clear()

  def allTasksComplete: Boolean = remainingTasks.isEmpty && startedTasks.isEmpty

  private val remainingTasks: mutable.Set[TaskWorkerTask] = mutable.Set.empty
  private val startedTasks: mutable.Set[TaskWorkerTask] = mutable.Set.empty

  protected def generateTasks: Iterable[TaskWorkerTask]

}
