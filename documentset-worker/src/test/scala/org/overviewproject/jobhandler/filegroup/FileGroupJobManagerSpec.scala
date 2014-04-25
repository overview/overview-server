package org.overviewproject.jobhandler.filegroup

import akka.actor._
import akka.testkit._

import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.{ Before, Specification }


class FileGroupJobManagerSpec extends Specification {

  "FileGroupJobManager" should {

    "create a job with a documentset" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      createJobWasCalledWith(fileGroupId, lang, suppliedStopWords, importantWords)
    }

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
            new TestFileGroupJobManager(fileGroupJobQueue.ref, clusteringJobQueue.ref))
      }

      def createJobWasCalledWith(fileGroupId: Long, lang: String,
                                 suppliedStopWords: String, importantWords: String) = {

        val pendingCalls = fileGroupJobManager.underlyingActor
          .createJobCallsInProgress
        awaitCond(pendingCalls.isCompleted)
        fileGroupJobManager.underlyingActor
          .storedCreateJobCalls must contain((fileGroupId, lang, suppliedStopWords, importantWords))
      }

    }

    
  }
}