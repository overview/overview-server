package org.overviewproject.jobhandler.filegroup.task

import scala.collection.mutable

/**
 * Track the tasks to create [[File]]s from [[UploadedFile]]s to determine when all tasks are complete
 * 
 * @todo Generate tasks to create documents
 */
class TaskTracker(uploadedFileIds: Set[Long]) {

    private val remainingTasks: mutable.Set[Long] = mutable.Set(uploadedFileIds.toSeq:_*)
    
    private val startedTasks: mutable.Set[Long] = mutable.Set.empty
    
    def startTask(uploadedFileId: Long) = {
      remainingTasks -= uploadedFileId
      startedTasks += uploadedFileId
    }
    
   def completeTask(uploadedFileId: Long): Unit = startedTasks -= uploadedFileId
   
   def removeNotStartedTasks: Unit = remainingTasks.clear()
   
   def allTasksComplete: Boolean = remainingTasks.isEmpty && startedTasks.isEmpty
}