package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef
import com.overviewdocs.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import com.overviewdocs.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks


/**
 * Creates the task needed to delete an uploaded `FileGroup`
 */
class DeleteFileGroupJobShepherd(documentSetId: Long, fileGroupId: Long, taskQueue: ActorRef) extends JobShepherd {

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val deleteTasks = Iterable(DeleteFileUploadJob(documentSetId, fileGroupId))
    taskQueue ! AddTasks(deleteTasks)

    deleteTasks
  }
}
