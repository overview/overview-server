package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

class RoundRobinTaskQueue extends TaskQueue {
  override def isEmpty: Boolean = queues.isEmpty

  override def addTask(task: TaskWorkerTask): TaskQueue = {
    queues
      .find(_._1 == task.documentSetId) match {
      case Some((ds, q)) => q += task
      case None => queues += ((task.documentSetId, mutable.Queue(task)))
    }
    
    this
  }

  override def addTasks(tasks: Iterable[TaskWorkerTask]): TaskQueue = {
    tasks.map(addTask)
    this
  }

  override def dequeue: TaskWorkerTask = {
    val documentSetTasks = queues.dequeue
    val task = documentSetTasks._2.dequeue

    if (!documentSetTasks._2.isEmpty) queues += documentSetTasks

    task
  }

  override def dequeueAll(p: TaskWorkerTask => Boolean): Seq[TaskWorkerTask] = {
    val tasks = queues.map(_._2.dequeueAll(p))
    queues.dequeueAll(_._2.isEmpty)
    
    tasks.flatten
  }

  private val queues: mutable.Queue[(Long, mutable.Queue[TaskWorkerTask])] = mutable.Queue.empty

}