package org.overviewproject.jobhandler.filegroup

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import akka.actor._
import akka.agent._
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.CancelClusterFileGroupCommand
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
  protected val cancelCommand = CancelClusterFileGroupCommand(documentSetId, fileGroupId) 
    
}

trait StorageMonitor extends JobParameters {
  self: TestFileGroupJobManager =>

  import ExecutionContext.Implicits.global
    
  private val updateJobStateParameters: Agent[Queue[Long]] = Agent(Queue.empty)
  
  class MockStorage extends Storage {
    override def findInProgressJobInformation: Iterable[(Long, Long)] = loadInterruptedJobs
    override def findCancelledJobInformation: Iterable[(Long, Long)] = loadCancelledJobs
    
    override def updateJobState(documentSetId: Long): Unit = updateJobStateParameters send (_.enqueue(documentSetId))
  }

  override protected val storage = new MockStorage
  
  def updateJobCallsInProgress = updateJobStateParameters.future
  def updateJobCallParameters = updateJobStateParameters.get
}

class TestFileGroupJobManager(
  override protected val fileGroupJobQueue: ActorRef,
  override protected val clusteringJobQueue: ActorRef,
  interruptedJobs: Seq[(Long, Long)],
  cancelledJobs: Seq[(Long, Long)]) extends FileGroupJobManager with StorageMonitor {
  
  protected def loadInterruptedJobs: Seq[(Long, Long)] = interruptedJobs
  protected def loadCancelledJobs: Seq[(Long, Long)] = cancelledJobs
}

