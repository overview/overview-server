package org.overviewproject.jobhandler.filegroup

import akka.agent._
import akka.actor._
import scala.collection.mutable.Queue
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.ClusterFileGroupCommand

trait JobParameters {
  protected val documentSetId = 1l
  protected val fileGroupId = 2l
  protected val title = "title"
  protected val lang = "en"
  protected val splitDocuments = false
  protected val importantWords = "important words"
  protected val suppliedStopWords = "stop words"

  protected val clusterCommand =
    ClusterFileGroupCommand(documentSetId, fileGroupId, title, lang, suppliedStopWords, importantWords)
}

trait StorageMonitor extends JobParameters {
  self: FileGroupJobManager =>

  import ExecutionContext.Implicits.global

  private val storedUpdateJobStateParameters: Agent[Queue[Long]] = Agent(Queue.empty)
  
  class MockStorage extends Storage {
    override def updateJobState(documentSetId: Long): Unit = storedUpdateJobStateParameters send (_ += documentSetId)
  }

  override protected val storage = new MockStorage

  def updateJobStateCallsInProgress: Future[Queue[Long]] = storedUpdateJobStateParameters.future
  def storedUpdateJobStateCalls: Iterable[Long] = storedUpdateJobStateParameters.get
}

class TestFileGroupJobManager(
  override protected val fileGroupJobQueue: ActorRef,
  override protected val clusteringJobQueue: ActorRef) extends FileGroupJobManager with StorageMonitor

