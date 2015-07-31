package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

class RoundRobinTaskQueue extends TaskQueue {
  override def isEmpty: Boolean = order.isEmpty

  override def addTask(task: TaskWorkerTask): TaskQueue = {
    val documentSetQueue = queues.getOrElseUpdate(task.documentSetId, mutable.Queue.empty)

    documentSetQueue += task
    if (!order.exists(_ == task.documentSetId)) order += task.documentSetId
    
    this
  }

  override def addTasks(tasks: Iterable[TaskWorkerTask]): TaskQueue = {
    tasks.map(addTask)
    this
  }

  override def dequeue: TaskWorkerTask = {
    val documentSetId = order.dequeue

    val taskQueue = queues.get(documentSetId).get

    val task = taskQueue.dequeue
    
    if (taskQueue.isEmpty) queues -= task.documentSetId
    else order += documentSetId
    
    task
  }

  override def dequeueAll(p: TaskWorkerTask => Boolean): Seq[TaskWorkerTask] = {
    val tasks = order.map(
      queues.get(_).get
        .dequeueAll(p))

    queues.keys.map { ds =>
      if (queues.get(ds).get.isEmpty) {
        queues -= ds
        order.dequeueAll(_ == ds)
      }
    }

    tasks.flatten
  }

  private val order: mutable.Queue[Long] = mutable.Queue.empty
  private val queues: mutable.Map[Long, mutable.Queue[TaskWorkerTask]] = mutable.HashMap.empty

}