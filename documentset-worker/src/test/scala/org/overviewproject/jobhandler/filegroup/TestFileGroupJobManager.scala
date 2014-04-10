package org.overviewproject.jobhandler.filegroup

import org.overviewproject.jobs.models.ClusterFileGroup
import akka.agent._
import akka.actor._
import scala.collection.mutable.Queue
import scala.concurrent.Future
import scala.concurrent.ExecutionContext


trait JobParameters {
  protected val documentSetId = 1l
  protected val fileGroupId = 2l
  protected val title = "title"
  protected val lang = "en"
  protected val importantWords = "important words"
  protected val suppliedStopWords = "stop words"

  protected val clusterCommand = ClusterFileGroup(fileGroupId, title, lang, suppliedStopWords, importantWords)
}

trait StorageMonitor extends JobParameters {
  self: FileGroupJobManager =>
    
  type CreateJobParameterList = (Long, String, String, String)
  import ExecutionContext.Implicits.global

  private val storedCreateJobParameters: Agent[Queue[CreateJobParameterList]] = Agent(Queue.empty)

  class MockStorage extends Storage {
    override def createDocumentSetWithJob(fileGroupId: Long, lang: String,
                                          suppliedStopWords: String, importantWords: String): Long = {
      storedCreateJobParameters send (_ += ((fileGroupId, lang, suppliedStopWords, importantWords)))

      documentSetId
    }

  }

  override protected val storage = new MockStorage

  def createJobCallsInProgress: Future[Queue[CreateJobParameterList]] = storedCreateJobParameters.future

  def storedCreateJobCalls: Iterable[CreateJobParameterList] = storedCreateJobParameters.get

}

class TestFileGroupJobManager(
  override protected val fileGroupJobQueue: ActorRef,
  override protected val clusteringJobQueue: ActorRef) extends FileGroupJobManager with StorageMonitor

