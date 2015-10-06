package com.overviewdocs.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.overviewdocs.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import com.overviewdocs.test.ActorSystemContext
import akka.testkit.TestProbe
import akka.actor.ActorRef
import com.overviewdocs.jobhandler.filegroup.FileGroupJobQueueProtocol.AddTasks
import org.specs2.mutable.Before
import com.overviewdocs.jobhandler.filegroup.ProgressReporterProtocol._
import com.overviewdocs.jobhandler.filegroup.JobDescription._
import com.overviewdocs.jobhandler.filegroup.task.UploadProcessOptions

class CreateDocumentsJobShepherdSpec extends Specification {
  sequential

  "CreateDocumentsJobShepherd" should {


    "Step through all sets of tasks" in new JobShepherdContext {
      jobShepherd.createTasks

      jobShepherd.allTasksComplete must beFalse

      stepThroughTask(createAliasTask)
      stepThroughTask(createDocumentsTask)
      stepThroughTask(completeDocumentSetTask, isFinalStep = true)
    }

    "report progress" in new JobShepherdContext {
      jobShepherd.createTasks

      jobShepherd.startTask(createAliasTask)
      progressReporter.expectMsg(StartJob(documentSetId, 3, ProcessUpload))
      progressReporter.expectMsg(StartJobStep(documentSetId, 1, 0.05, ExtractText))
      progressReporter.expectMsg(StartTask(documentSetId))

      jobShepherd.completeTask(createAliasTask)
      progressReporter.expectMsg(CompleteTask(documentSetId))
      progressReporter.expectMsg(CompleteJobStep(documentSetId))

      jobShepherd.startTask(createDocumentsTask)

      progressReporter.expectMsg(StartJobStep(documentSetId, 1, 0.90, ExtractText))
      progressReporter.expectMsg(StartTask(documentSetId))

      jobShepherd.completeTask(createDocumentsTask)
      progressReporter.expectMsg(CompleteTask(documentSetId))
      progressReporter.expectMsg(CompleteJobStep(documentSetId))

      jobShepherd.startTask(completeDocumentSetTask)
      progressReporter.expectMsg(StartJobStep(documentSetId, 1, 0.05, CreateDocument))
      progressReporter.expectMsg(StartTask(documentSetId))

      jobShepherd.completeTask(completeDocumentSetTask)
      progressReporter.expectMsg(CompleteTask(documentSetId))
      progressReporter.expectMsg(CompleteJobStep(documentSetId))

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
      var createAliasTask: CreateSearchIndexAlias = _
      var createDocumentsTask: CreateDocuments = _
      var completeDocumentSetTask: CompleteDocumentSet = _

      override def before = {
        jobQueue = TestProbe()
        progressReporter = TestProbe()
        documentIdSupplier = TestProbe()
        createAliasTask = CreateSearchIndexAlias(documentSetId, fileGroupId)
        createDocumentsTask = CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier.ref)
        completeDocumentSetTask = CompleteDocumentSet(documentSetId, fileGroupId)

        jobShepherd = new TestCreateDocumentsJobShepherd(documentSetId, fileGroupId, options,
          jobQueue.ref, progressReporter.ref, documentIdSupplier.ref, Set(uploadedFileId))
      }

      protected def stepThroughTask(task: TaskWorkerTask, isFinalStep: Boolean = false) = {
        jobShepherd.startTask(task)
        jobQueue.expectMsg(AddTasks(Set(task)))

        jobShepherd.completeTask(task)
        jobShepherd.allTasksComplete must be equalTo(isFinalStep)

      }

    }
  }
}
