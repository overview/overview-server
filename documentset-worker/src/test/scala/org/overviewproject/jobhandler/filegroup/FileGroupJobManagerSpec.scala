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
      fileGroupJobQueue.reply(FileGroupDocumentsCreated(fileGroupId))

      clusteringJobQueue.expectMsg(ClusterDocumentSet(fileGroupId))
    }

    "restart in progress jobs found during startup" in new RestartingFileGroupJobManagerContext {

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(interruptedDocumentSet1, fileGroup1))
      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(interruptedDocumentSet2, fileGroup2))
    }

    "cancel text extraction when requested" in {
      todo
    }

    "don't start clustering cancelled job" in {
      todo
    }

    abstract class FileGroupJobManagerContext extends ActorSystemContext with Before with JobParameters {

      var fileGroupJobManager: TestActorRef[TestFileGroupJobManager] = _
      var fileGroupJobQueue: TestProbe = _
      var clusteringJobQueue: TestProbe = _

      override def before = {
        fileGroupJobQueue = TestProbe()
        clusteringJobQueue = TestProbe()

        fileGroupJobManager = TestActorRef(
        new TestFileGroupJobManager(fileGroupJobQueue.ref, clusteringJobQueue.ref, interruptedJobs))

      }

      protected def interruptedJobs: Seq[(Long, Long)] = Seq.empty
    }

    abstract class RestartingFileGroupJobManagerContext extends FileGroupJobManagerContext {

      val interruptedDocumentSet1: Long = 10l
      val interruptedDocumentSet2: Long = 20l
      val fileGroup1: Long = 15l
      val fileGroup2: Long = 25l
      
      override protected def interruptedJobs: Seq[(Long, Long)] = Seq(
        (interruptedDocumentSet1, fileGroup1),
        (interruptedDocumentSet2, fileGroup2)
      )

    }
  }
}