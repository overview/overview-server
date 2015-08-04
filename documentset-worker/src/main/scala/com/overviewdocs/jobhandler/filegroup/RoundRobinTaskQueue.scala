package com.overviewdocs.jobhandler.filegroup

import scala.collection.mutable
import com.overviewdocs.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask


/**
 * A [[TaskQueue]] that hands out tasks in round robin order based on the `documentSetId`
 * in the [[TaskWorkerTask]].
 * 
 * [[RoundRobinTaskQueue]] is _mutable_ -- adding and removing tasks changes the instance.
 */
class RoundRobinTaskQueue extends TaskQueue {
  override def isEmpty: Boolean = queues.isEmpty

  /**
   * Adds a task to the queue. If there are no other tasks with the same `documentSetId` in the queue,
   * the task is added to the end of the current round-robin sequence.
   */
  override def addTask(task: TaskWorkerTask): TaskQueue = {
    queues
      .find(_.documentSetId == task.documentSetId) match {
      case Some(queue) => queue.tasks += task
      case None => queues += createDocumentSetTaskQueue(task)
    }
    
    this
  }

  /** 
   *  Actually adds tasks separately, since we can't guarantee that all `tasks` have the same `documentSetId`
   */
  override def addTasks(tasks: Iterable[TaskWorkerTask]): TaskQueue = {
    tasks.map(addTask)
    this
  }

  /**
   * @returns the next [[TaskWorkerTask]] in round-robin order, based on the `documentSetId`.
   */
  override def dequeue: TaskWorkerTask = {
    val documentSetTasks = queues.dequeue
    val task = documentSetTasks.tasks.dequeue

    if (!documentSetTasks.tasks.isEmpty) queues += documentSetTasks

    task
  }

  /**
   * Removes all [[TaskWorkerTasks]] matching the specified predicate.
   * The tasks are returned ordered by `documentSetId`, not in round-robin order.
   */
  override def removeAll(p: TaskWorkerTask => Boolean): Seq[TaskWorkerTask] = {
    val tasks = queues.map(_.tasks.dequeueAll(p))
    queues.dequeueAll(_.tasks.isEmpty)
    
    tasks.flatten
  }

  private case class DocumentSetTaskQueue(documentSetId: Long, tasks: mutable.Queue[TaskWorkerTask])
  private val queues: mutable.Queue[DocumentSetTaskQueue] = mutable.Queue.empty
  
  private def createDocumentSetTaskQueue(task: TaskWorkerTask) = 
    DocumentSetTaskQueue(task.documentSetId, mutable.Queue(task))

}