package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestProbe
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks
import org.specs2.mutable.Before
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.JobDescription._

class CreateDocumentsJobShepherdSpec extends Specification {

  "CreateDocumentsJobShepherd" should {

    "start CreateDocumentsTask when all pages have been created" in new JobShepherdContext {
      jobShepherd.createTasks
      jobShepherd.startTask(task)
      jobShepherd.completeTask(task)

      jobQueue.expectMsg(AddTasks(Set(task)))
      jobQueue.expectMsg(AddTasks(Set(createDocumentsTask)))
    }

    "report all tasks completed when CreateDocumentsTask is complete" in new JobShepherdContext {

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
    
    
    "notify progress reporter about job steps" in new JobShepherdContext {
      
      jobShepherd.createTasks
      progressReporter.expectMsg(StartJob(documentSetId, 2, jobDescription))
      progressReporter.expectMsg(StartJobStep(documentSetId, 1, 0.75, stepDescription1))
      jobShepherd.startTask(task)
      jobShepherd.completeTask(task)
      
      progressReporter.expectMsg(StartTask(documentSetId, uploadedFileId))
      progressReporter.expectMsg(CompleteTask(documentSetId, uploadedFileId))
      progressReporter.expectMsg(CompleteJobStep(documentSetId))
      
      progressReporter.expectMsg(StartJobStep(documentSetId, 1, 0.25, stepDescription2))
      
      jobShepherd.completeTask(createDocumentsTask)
      
      progressReporter.expectMsg(CompleteJobStep(documentSetId))
    }

    abstract class JobShepherdContext extends ActorSystemContext with Before {
      var jobQueue: TestProbe = _
      var progressReporter: TestProbe = _
      var jobShepherd: TestCreateDocumentsJobShepherd = _

      val documentSetId = 1l
      val fileGroupId = 1l
      val uploadedFileId = 10l
      val splitDocuments = true
      val task = CreatePagesTask(documentSetId, fileGroupId, uploadedFileId)
      val createDocumentsTask = CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments)
      val jobDescription = ExtractText
      val stepDescription1 = ExtractText
      val stepDescription2 = CreateDocument
      
      override def before = {
        jobQueue = TestProbe()
        progressReporter = TestProbe()

        jobShepherd = new TestCreateDocumentsJobShepherd(documentSetId, fileGroupId, splitDocuments,
          jobQueue.ref, progressReporter.ref, Set(uploadedFileId))
      }
    }
  }
}
