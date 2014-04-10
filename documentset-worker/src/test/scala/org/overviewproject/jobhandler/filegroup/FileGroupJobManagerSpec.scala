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

class StorageMonitor(override protected val fileGroupJobQueue: ActorRef) extends FileGroupJobManager {
  type CreateJobParameterList = (Long, Long, String, String, String)
  import ExecutionContext.Implicits.global

  private val storedCreateJobParameters: Agent[Queue[CreateJobParameterList]] = Agent(Queue.empty)

  class MockStorage extends Storage {
    override def createDocumentSet(title: String): Long = 1l

    override def createJob(documentSetId: Long, fileGroupId: Long, lang: String,
                           suppliedStopWords: String, importantWords: String): Unit =
      storedCreateJobParameters send (_ += ((documentSetId, fileGroupId, lang, suppliedStopWords, importantWords)))

  }

  override protected val storage = new MockStorage

  def createJobCallsInProgress: Future[Queue[CreateJobParameterList]] = storedCreateJobParameters.future

  def storedCreateJobCalls: Iterable[CreateJobParameterList] = storedCreateJobParameters.get

}

class FileGroupJobManagerSpec extends Specification {

  "FileGroupJobManager" should {

    abstract class FileGroupJobManagerContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val fileGroupId = 2l
      protected val title = "title"
      protected val lang = "en"
      protected val importantWords = "important words"
      protected val suppliedStopWords = "stop words"

      protected val clusterCommand = ClusterFileGroup(fileGroupId, title, lang, suppliedStopWords, importantWords)

      var fileGroupJobManager: TestActorRef[StorageMonitor] = _
      var fileGroupJobQueue: TestProbe = _

      override def before = {
        fileGroupJobQueue = TestProbe()
        fileGroupJobManager = TestActorRef(new StorageMonitor(fileGroupJobQueue.ref))
      }

      def createJobWasCalledWith(documentSetId: Long, fileGroupId: Long, lang: String,
                                 suppliedStopWords: String, importantWords: String) = {

        val pendingCalls = fileGroupJobManager.underlyingActor
          .createJobCallsInProgress
        awaitCond(pendingCalls.isCompleted)
        fileGroupJobManager.underlyingActor
          .storedCreateJobCalls must contain((documentSetId, fileGroupId, lang, suppliedStopWords, importantWords))
      }

    }

    "create a job with a documentset" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      createJobWasCalledWith(documentSetId, fileGroupId, lang, suppliedStopWords, importantWords)
    }

    "start text extraction job at text extraction job queue" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsg(CreateDocumentsFromFileGroup(fileGroupId, documentSetId))
    }

  }
}