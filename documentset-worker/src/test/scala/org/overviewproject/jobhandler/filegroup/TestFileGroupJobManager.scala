package org.overviewproject.jobhandler.filegroup

import akka.agent._
import akka.actor._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.ClusterFileGroupCommand
import scala.collection.immutable.Queue


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
  self: TestFileGroupJobManager =>

  import ExecutionContext.Implicits.global
    
  private val updateJobStateParameters: Agent[Queue[Long]] = Agent(Queue.empty)
  
  class MockStorage extends Storage {
    override def findInProgressJobInformation: Iterable[(Long, Long)] = loadInterruptedJobs
    override def updateJobState(documentSetId: Long): Unit = updateJobStateParameters send (_.enqueue(documentSetId))
  }

  override protected val storage = new MockStorage
  
  def updateJobCallsInProgress = updateJobStateParameters.future
  def updateJobCallParameters = updateJobStateParameters.get
}

class TestFileGroupJobManager(
  override protected val fileGroupJobQueue: ActorRef,
  override protected val clusteringJobQueue: ActorRef,
  interruptedJobs: Seq[(Long, Long)]) extends FileGroupJobManager with StorageMonitor {
  
  protected def loadInterruptedJobs: Seq[(Long, Long)] = interruptedJobs
}

