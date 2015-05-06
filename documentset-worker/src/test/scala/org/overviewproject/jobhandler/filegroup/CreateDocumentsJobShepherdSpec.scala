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
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions

class CreateDocumentsJobShepherdSpec extends Specification {

  "CreateDocumentsJobShepherd" should {

    "send CreateDocuments tasks to job queue" in new JobShepherdContext {
      jobShepherd.createTasks

      jobQueue.expectMsg(AddTasks(Set(task)))
    }

    "report progress" in new JobShepherdContext {
      jobShepherd.createTasks
      
      progressReporter.expectMsg(StartJob(documentSetId, 1, ExtractText))
      
      jobShepherd.startTask(task)
      progressReporter.expectMsg(StartTask(documentSetId, uploadedFileId))
      
      jobShepherd.completeTask(task)
      progressReporter.expectMsg(CompleteTask(documentSetId, uploadedFileId))
      
      progressReporter.expectMsg(CompleteJob(documentSetId))
    }

    abstract class JobShepherdContext extends ActorSystemContext with Before {
      var jobQueue: TestProbe = _
      var progressReporter: TestProbe = _
      var documentIdSupplier: TestProbe = _
      
      var jobShepherd: TestCreateDocumentsJobShepherd = _

      val documentSetId = 1l
      val fileGroupId = 1l
      val uploadedFileId = 10l
      val options = UploadProcessOptions("en", true)
      var task: CreateDocuments = _

      
      override def before = {
        jobQueue = TestProbe()
        progressReporter = TestProbe()
        documentIdSupplier = TestProbe()
        task = CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref)
        
        jobShepherd = new TestCreateDocumentsJobShepherd(documentSetId, fileGroupId, options,
          jobQueue.ref, progressReporter.ref, documentIdSupplier.ref, Set(uploadedFileId))
      }
    }
  }
}
