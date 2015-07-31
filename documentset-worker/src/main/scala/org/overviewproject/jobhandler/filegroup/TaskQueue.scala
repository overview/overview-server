package org.overviewproject.jobhandler.filegroup

import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

trait TaskQueue {
  def isEmpty: Boolean
  def addTask(task: TaskWorkerTask): TaskQueue
  def addTasks(tasks: Iterable[TaskWorkerTask]): TaskQueue
  def dequeue: TaskWorkerTask
  def removeAll(p: TaskWorkerTask => Boolean): Seq[TaskWorkerTask]
}