package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder


trait JobTracker {
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

class DeleteFileGroupJobTracker(documentSetId: Long, fileGroupId: Long, taskQueue: ActorRef) extends JobTracker {
  
  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val deleteTasks = Iterable(DeleteFileUploadJob(documentSetId, fileGroupId))
    taskQueue ! AddTasks(deleteTasks)

    deleteTasks
  }
}

trait CreateDocumentsJobTracker extends JobTracker {
  val documentSetId: Long
  val fileGroupId: Long
  val taskQueue: ActorRef

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val tasks = uploadedFilesInFileGroup(fileGroupId).map(CreatePagesTask(documentSetId, fileGroupId, _))

    taskQueue ! AddTasks(tasks)
    tasks
  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  protected val storage: Storage
  protected trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
  }
}

class CreateDocumentsJobTrackerImpl(val documentSetId: Long, val fileGroupId: Long, val taskQueue: ActorRef) extends CreateDocumentsJobTracker {

  override protected val storage = new DatabaseStorage

  protected class DatabaseStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Set[Long] = Database.inTransaction {
      GroupedFileUploadFinder.byFileGroup(fileGroupId).toIds.toSet
    }

  }
}