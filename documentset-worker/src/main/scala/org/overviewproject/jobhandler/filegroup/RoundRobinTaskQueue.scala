package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

class RoundRobinTaskQueue extends TaskQueue {
  override def isEmpty: Boolean = queue.isEmpty
  
  override def addTask(task: TaskWorkerTask): TaskQueue = {
    queue += task
    this
  }
  
  override def addTasks(tasks: Iterable[TaskWorkerTask]): TaskQueue = {
    queue ++= tasks
    this
  }
  
  override def dequeue: TaskWorkerTask = queue.dequeue
  override def dequeueAll(p: TaskWorkerTask => Boolean): Seq[TaskWorkerTask] = queue.dequeueAll(p)
  
  private val queue: mutable.Queue[TaskWorkerTask] = mutable.Queue.empty

}