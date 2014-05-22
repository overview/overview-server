package org.overviewproject.jobhandler.filegroup

import akka.actor._
import akka.testkit._
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.{ Before, Specification }

class FileGroupJobManagerSpec extends Specification {

  "FileGroupJobManager" should {

    "start text extraction job at text extraction job queue" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(documentSetId, fileGroupId))
    }

    "start clustering job when text extraction is complete" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsgType[CreateDocumentsFromFileGroup]
      fileGroupJobQueue.reply(FileGroupDocumentsCreated(documentSetId))

      clusteringJobQueue.expectMsg(ClusterDocumentSet(documentSetId))
    }

    "update job state when clustering request is received" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      updateJobStateWasCalled(documentSetId)
    }

    "restart in progress jobs and cancel cancelled jobs found during startup" in new RestartingFileGroupJobManagerContext {

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(interruptedDocumentSet1, fileGroup1))
      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(interruptedDocumentSet2, fileGroup2))

      fileGroupJobQueue.expectMsg(CancelFileUpload(cancelledDocumentSet1, fileGroup3))
      fileGroupJobQueue.expectMsg(CancelFileUpload(cancelledDocumentSet2, fileGroup4))
    }

    
    "cancel text extraction when requested" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand
      fileGroupJobManager ! cancelCommand

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(documentSetId, fileGroupId))
      fileGroupJobQueue.expectMsg(CancelFileUpload(documentSetId, fileGroupId))
    }

    "don't start clustering cancelled job" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand
      fileGroupJobManager ! cancelCommand

      fileGroupJobQueue.expectMsgType[CreateDocumentsFromFileGroup]
      fileGroupJobQueue.expectMsgType[CancelFileUpload]
      fileGroupJobQueue.reply(FileGroupDocumentsCreated(documentSetId))

      clusteringJobQueue.expectNoMsg
    }

    "delete cancelled job after cancellation is complete" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand
      fileGroupJobManager ! cancelCommand

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(documentSetId, fileGroupId))
      fileGroupJobQueue.expectMsg(CancelFileUpload(documentSetId, fileGroupId))
      fileGroupJobQueue.reply(FileGroupDocumentsCreated(documentSetId))

      fileGroupJobQueue.expectMsg(DeleteFileUpload(documentSetId, fileGroupId))

    }

    abstract class FileGroupJobManagerContext extends ActorSystemContext with Before with JobParameters {

      var fileGroupJobManager: TestActorRef[TestFileGroupJobManager] = _
      var fileGroupJobQueue: TestProbe = _
      var clusteringJobQueue: TestProbe = _

      override def before = {
        fileGroupJobQueue = TestProbe()
        clusteringJobQueue = TestProbe()
        fileGroupJobManager = TestActorRef(
          new TestFileGroupJobManager(fileGroupJobQueue.ref, clusteringJobQueue.ref, interruptedJobs, cancelledJobs))

      }

      protected def interruptedJobs: Seq[(Long, Long)] = Seq.empty
      protected def cancelledJobs: Seq[(Long, Long)] = Seq.empty

      protected def updateJobStateWasCalled(documentSetId: Long) = {
        val pendingCalls = fileGroupJobManager.underlyingActor.updateJobCallsInProgress
        awaitCond(pendingCalls.isCompleted)

        fileGroupJobManager.underlyingActor.updateJobCallParameters.headOption must beSome(documentSetId)
      }
    }

    abstract class RestartingFileGroupJobManagerContext extends FileGroupJobManagerContext {
      val interruptedDocumentSet1: Long = 10l
      val interruptedDocumentSet2: Long = 20l
      val cancelledDocumentSet1: Long = 30l
      val cancelledDocumentSet2: Long = 40l

      val fileGroup1: Long = 15l
      val fileGroup2: Long = 25l
      val fileGroup3: Long = 35l
      val fileGroup4: Long = 45l

      override protected def interruptedJobs: Seq[(Long, Long)] = Seq(
        (interruptedDocumentSet1, fileGroup1),
        (interruptedDocumentSet2, fileGroup2))

      override protected def cancelledJobs: Seq[(Long, Long)] = Seq(
        (cancelledDocumentSet1, fileGroup3),
        (cancelledDocumentSet2, fileGroup4))
    }
  }
}