package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks


class DeleteFileGroupJobTracker(documentSetId: Long, fileGroupId: Long, taskQueue: ActorRef) extends JobTracker {

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val deleteTasks = Iterable(DeleteFileUploadJob(documentSetId, fileGroupId))
    taskQueue ! AddTasks(deleteTasks)

    deleteTasks
  }
}
