package org.overviewproject.jobhandler.filegroup

import org.specs2.specification.Scope
import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

class RoundRobinTaskQueueSpec extends Specification {

  
  "RoundRobinTaskQueue" should {
    
    "add and remove tasks" in new TaskContext {
      
      taskQueue.isEmpty must beTrue
      
      taskQueue.addTasks(Seq(task))
      taskQueue.isEmpty must beFalse
    
      taskQueue.dequeue must be equalTo(task)
      
      taskQueue.isEmpty must beTrue
    }
  }
  
  trait TaskContext extends Scope {
    val documentSetId = 1l
    val fileGroupId = 2l
    
    val taskQueue = new RoundRobinTaskQueue
    val task = TestTask(documentSetId, fileGroupId, 1)
  }
  
  case class TestTask(documentSetId: Long, fileGroupId: Long, taskId: Int) extends TaskWorkerTask
}