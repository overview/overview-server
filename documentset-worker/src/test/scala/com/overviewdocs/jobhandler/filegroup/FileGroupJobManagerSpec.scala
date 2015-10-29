package com.overviewdocs.jobhandler.filegroup

import akka.actor._
import akka.testkit._

import com.overviewdocs.jobhandler.filegroup.ClusteringJobQueueProtocol._
import com.overviewdocs.jobhandler.filegroup.FileGroupJobQueueProtocol._
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.tree.DocumentSetCreationJobType._
import com.overviewdocs.tree.orm.DocumentSetCreationJob
import com.overviewdocs.tree.orm.DocumentSetCreationJobState._
import org.specs2.mutable.{ Before, Specification }

class FileGroupJobManagerSpec extends Specification {
  sequential

  "FileGroupJobManager" should {

    "start text extraction job at text extraction job queue" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, options)))
    }

    "start clustering job when text extraction is complete" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsgType[SubmitJob]
      fileGroupJobQueue.reply(JobCompleted(documentSetId))

      clusteringJobQueue.expectMsg(ClusterDocumentSet(documentSetId))
    }

    "update job state when clustering request is received" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      updateJobStateWasCalled(documentSetId)
    }

    "restart in progress jobs found during startup" in new RestartingFileGroupJobManagerContext {
      fileGroupJobQueue.expectMsg(SubmitJob(interruptedDocumentSet1, CreateDocumentsJob(fileGroup1, options)))
      fileGroupJobQueue.expectMsg(SubmitJob(interruptedDocumentSet2, CreateDocumentsJob(fileGroup2, options)))
    }

    "fail job if restart limit is reached" in new RestartLimitContext {
      failJobWasCalled(interruptedDocumentSet)
    }

    "increase retryAttempts when restarting jobs" in new RestartingFileGroupJobManagerContext {
      increaseRetryAttemptsWasCalled(interruptedDocumentSet1, fileGroup1)
      increaseRetryAttemptsWasCalled(interruptedDocumentSet2, fileGroup2)
    }

    "don't start text extraction if no job is found" in new NoJobContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectNoMsg
    }
    
    "try again if no job is found initially" in new DelayedJobContext {
      fileGroupJobManager ! clusterCommand
      
      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, options)))
    }

    abstract class FileGroupJobManagerContext extends ActorSystemContext with Before with JobParameters {

      var fileGroupJobManager: TestActorRef[TestFileGroupJobManager] = _
      var fileGroupJobQueue: TestProbe = _
      var clusteringJobQueue: TestProbe = _

      override def before = {
        fileGroupJobQueue = TestProbe()
        clusteringJobQueue = TestProbe()
        fileGroupJobManager = TestActorRef(
          new TestFileGroupJobManager(fileGroupJobQueue.ref, clusteringJobQueue.ref, uploadJob, interruptedJobs)
        )
      }

      private def jm = fileGroupJobManager.underlyingActor
      
      protected def uploadJob: Option[DocumentSetCreationJob] = Some(
        DocumentSetCreationJob(documentSetId = documentSetId, jobType = FileUpload, fileGroupId = Some(fileGroupId),
          state = FilesUploaded))

      protected def interruptedJobs: Seq[(Long, Long, Int)] = Seq.empty

      protected def updateJobStateWasCalled(documentSetId: Long) = jm.updateJobStateFn.wasCalledWith(documentSetId)
      

      protected def failJobWasCalled(documentSetId: Long) = {
        def matchDocumentSetId(job: DocumentSetCreationJob) = {
          job.documentSetId must be equalTo (documentSetId)
        }

        jm.failJobFn.wasCalledWithMatch(matchDocumentSetId _)
      }

      protected def increaseRetryAttemptsWasCalled(documentSetId: Long, fileGroupId: Long) = {
        def matchDsAndFg(job: DocumentSetCreationJob) = {
          job.documentSetId must be equalTo (documentSetId)
          job.fileGroupId must beSome(fileGroupId)
        }
        
        jm.increaseRetryAttemptFn.wasCalledWithMatch(matchDsAndFg)
      }
    }

    abstract class RestartingFileGroupJobManagerContext extends FileGroupJobManagerContext {
      val interruptedDocumentSet1: Long = 10l
      val interruptedDocumentSet2: Long = 20l

      val fileGroup1: Long = 15l
      val fileGroup2: Long = 25l
      val fileGroup3: Long = 35l
      val fileGroup4: Long = 45l

      override protected def interruptedJobs: Seq[(Long, Long, Int)] = Seq(
        (interruptedDocumentSet1, fileGroup1, 0),
        (interruptedDocumentSet2, fileGroup2, 0))
    }

    abstract class NoJobContext extends FileGroupJobManagerContext {
      override protected def uploadJob: Option[DocumentSetCreationJob] = None
    }
    
    abstract class DelayedJobContext extends FileGroupJobManagerContext {
      private var callCount = 0
      
      override protected def uploadJob: Option[DocumentSetCreationJob] = {
        callCount += 1
        
        if (callCount > 1) super.uploadJob
        else None
      }
    }

    abstract class RestartLimitContext extends FileGroupJobManagerContext {
      val interruptedDocumentSet: Long = 10l
      val fileGroup: Long = 15l

      override protected def interruptedJobs: Seq[(Long, Long, Int)] = Seq(
        (interruptedDocumentSet, fileGroup, 3))
    }
  }

}
