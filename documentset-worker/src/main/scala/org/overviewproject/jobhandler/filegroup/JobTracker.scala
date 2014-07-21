package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import scala.collection.mutable
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.CreatePagesTask
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder

trait JobTracker {
  def createTasks: Int = {
    val tasks = generateTasks

    remainingTasks ++= tasks
    
    remainingTasks.size
  }

  def startTask(uploadedFileId: Long): Unit = {
    remainingTasks -= uploadedFileId
    startedTasks += uploadedFileId
  }

  def completeTask(uploadedFileId: Long): Unit = startedTasks -= uploadedFileId
  def removeNotStartedTasks: Unit = remainingTasks.clear()

  def allTasksComplete: Boolean = remainingTasks.isEmpty && startedTasks.isEmpty

  private val remainingTasks: mutable.Set[Long] = mutable.Set.empty
  private val startedTasks: mutable.Set[Long] = mutable.Set.empty

  protected def generateTasks: Set[Long]

}

trait CreateDocumentsJobTracker extends JobTracker {
  val documentSetId: Long
  val fileGroupId: Long
  val taskQueue: ActorRef

  override protected def generateTasks: Set[Long] = {
    val fileIds = uploadedFilesInFileGroup(fileGroupId)

    addNewTasksToQueue(fileIds)
    fileIds
  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  private def addNewTasksToQueue(uploadedFileIds: Set[Long]): Unit = {
    val newTasks = uploadedFileIds.map(CreatePagesTask(documentSetId, fileGroupId, _))

    taskQueue ! AddTasks(newTasks)
  }

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