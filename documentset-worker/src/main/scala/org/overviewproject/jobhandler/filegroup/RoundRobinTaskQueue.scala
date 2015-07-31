package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

class RoundRobinTaskQueue extends TaskQueue {
  override def isEmpty: Boolean = queues.isEmpty

  override def addTask(task: TaskWorkerTask): TaskQueue = {
    queues
      .find(_.documentSetId == task.documentSetId) match {
      case Some(queue) => queue.tasks += task
      case None => queues += DocumentSetTaskQueue(task.documentSetId, mutable.Queue(task))
    }
    
    this
  }

  override def addTasks(tasks: Iterable[TaskWorkerTask]): TaskQueue = {
    tasks.map(addTask)
    this
  }

  override def dequeue: TaskWorkerTask = {
    val documentSetTasks = queues.dequeue
    val task = documentSetTasks.tasks.dequeue

    if (!documentSetTasks.tasks.isEmpty) queues += documentSetTasks

    task
  }

  override def dequeueAll(p: TaskWorkerTask => Boolean): Seq[TaskWorkerTask] = {
    val tasks = queues.map(_.tasks.dequeueAll(p))
    queues.dequeueAll(_.tasks.isEmpty)
    
    tasks.flatten
  }

  private case class DocumentSetTaskQueue(documentSetId: Long, tasks: mutable.Queue[TaskWorkerTask])
  private val queues: mutable.Queue[DocumentSetTaskQueue] = mutable.Queue.empty

}