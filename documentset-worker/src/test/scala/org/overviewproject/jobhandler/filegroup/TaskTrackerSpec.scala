package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification

class TaskTrackerSpec extends Specification {
  
  "TaskTracker" should {
    
    "track when all tasks are complete" in {
      val task1 = 1l
      val task2 = 2l
      val taskTracker = new TaskTracker(Set(task1, task2))
      
      taskTracker.startTask(task1)
      taskTracker.allTasksComplete must beFalse
      
      taskTracker.startTask(task2)
      taskTracker.completeTask(task2)
      taskTracker.allTasksComplete must beFalse
      
      taskTracker.completeTask(task1)
      taskTracker.allTasksComplete must beTrue
    }
    
    "remove not-started tasks" in {

      val task1 = 1l
      val task2 = 2l
      val taskTracker = new TaskTracker(Set(task1, task2))
      
      taskTracker.startTask(task1)

      taskTracker.removeNotStartedTasks
      
      taskTracker.allTasksComplete must beFalse
      
      taskTracker.completeTask(task1)
      taskTracker.allTasksComplete must beTrue
      
    }
  }

}