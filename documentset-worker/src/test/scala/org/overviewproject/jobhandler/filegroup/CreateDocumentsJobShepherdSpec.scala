package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestProbe
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks


class CreateDocumentsJobShepherdSpec extends Specification {

  "CreateDocumentsJobShepherd" should {
    
    "start CreateDocumentsTask when all pages have been created" in new ActorSystemContext {
      val jobQueue = TestProbe()
      val progressReporter = TestProbe()
      val documentSetId = 1l
      val fileGroupId = 1l
      val uploadedFileId = 10l
      val task = CreatePagesTask(documentSetId, fileGroupId, uploadedFileId)
      val createDocumentsTask = CreateDocumentsTask(documentSetId, fileGroupId, false)
      
      val jobShepherd = new TestCreateDocumentsJobShepherd(documentSetId, fileGroupId, 
          jobQueue.ref, progressReporter.ref, Set(uploadedFileId))
      
      jobShepherd.createTasks
      jobShepherd.startTask(task)
      jobShepherd.completeTask(task)
      
      jobQueue.expectMsg(AddTasks(Set(task)))
      jobQueue.expectMsg(AddTasks(Set(createDocumentsTask)))
    }
    
    "report all tasks completed when CreateDocumentsTask is complete" in new ActorSystemContext {
      val jobQueue = TestProbe()
      val progressReporter = TestProbe()
      val documentSetId = 1l
      val fileGroupId = 1l
      val uploadedFileId = 10l
      val task = CreatePagesTask(documentSetId, fileGroupId, uploadedFileId)
      val createDocumentsTask = CreateDocumentsTask(documentSetId, fileGroupId, false)
      
      val jobShepherd = new TestCreateDocumentsJobShepherd(documentSetId, fileGroupId, 
          jobQueue.ref, progressReporter.ref, Set(uploadedFileId))
      
      jobShepherd.createTasks
      jobShepherd.startTask(task)
      jobShepherd.completeTask(task)
      
      jobQueue.expectMsg(AddTasks(Set(task)))
      jobQueue.expectMsg(AddTasks(Set(createDocumentsTask)))
      
      jobShepherd.allTasksComplete must beFalse
      jobShepherd.startTask(createDocumentsTask)
      jobShepherd.completeTask(createDocumentsTask)
      jobShepherd.allTasksComplete must beTrue
      
    }
  }
}
