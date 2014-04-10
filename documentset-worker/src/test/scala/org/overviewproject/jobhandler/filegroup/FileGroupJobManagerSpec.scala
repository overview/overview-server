package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import akka.testkit._
import scala.collection.mutable.Queue
import akka.agent.Agent
import akka.testkit.CallingThreadDispatcher
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.overviewproject.jobs.models.ClusterFileGroup
import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._

class FileGroupJobManagerSpec extends Specification {

  "FileGroupJobManager" should {

    abstract class FileGroupJobManagerContext extends ActorSystemContext with Before with JobParameters {

      var fileGroupJobManager: TestActorRef[TestFileGroupJobManager] = _
      var fileGroupJobQueue: TestProbe = _

      override def before = {
        fileGroupJobQueue = TestProbe()
        fileGroupJobManager = TestActorRef(new TestFileGroupJobManager(fileGroupJobQueue.ref))
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

    "create a job with a documentset" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      createJobWasCalledWith(fileGroupId, lang, suppliedStopWords, importantWords)
    }

    "start text extraction job at text extraction job queue" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(fileGroupId, documentSetId))
    }

  }
}