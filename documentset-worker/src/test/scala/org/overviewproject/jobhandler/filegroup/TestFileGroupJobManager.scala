package org.overviewproject.jobhandler.filegroup

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import akka.actor._
import akka.agent._
import org.overviewproject.jobhandler.filegroup.FileGroupJobMessages._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

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

  private val updateJobStateParameters: Agent[Queue[(Long, DocumentSetCreationJobState)]] = Agent(Queue.empty)
  private val increaseRetryAttemptParameters: Agent[Queue[DocumentSetCreationJob]] = Agent(Queue.empty)
  
  class MockStorage extends Storage {
    override def findValidInProgressUploadJobs: Iterable[DocumentSetCreationJob] = loadInterruptedJobs
    override def findValidCancelledUploadJobs: Iterable[DocumentSetCreationJob] = loadCancelledJobs

    override def updateJobState(documentSetId: Long, jobState: DocumentSetCreationJobState): Option[DocumentSetCreationJob] = {
      updateJobStateParameters send (_.enqueue((documentSetId, jobState)))
      uploadJob
    }
    
    override def increaseRetryAttempts(job: DocumentSetCreationJob): Unit = 
      increaseRetryAttemptParameters send (_.enqueue(job))
  }

  override protected val storage = new MockStorage

  def updateJobCallsInProgress = updateJobStateParameters.future
  def updateJobCallParameters = updateJobStateParameters.get
  
  def increaseRetryAttemptsCallsInProgress = increaseRetryAttemptParameters.future
  def increaseRetryAttemptCallParameters = increaseRetryAttemptParameters.get
}

class TestFileGroupJobManager(
    override protected val fileGroupJobQueue: ActorRef,
    override protected val clusteringJobQueue: ActorRef,
    val uploadJob: Option[DocumentSetCreationJob],
    interruptedJobs: Seq[(Long, Long, Int)],
    cancelledJobs: Seq[(Long, Long)]) extends FileGroupJobManager with StorageMonitor {

  protected def loadInterruptedJobs: Seq[DocumentSetCreationJob] =
    for ((ds, fg, n) <- interruptedJobs)
      yield DocumentSetCreationJob(jobType = FileUpload, state = InProgress, 
          documentSetId = ds, fileGroupId = Some(fg), retryAttempts = n)

  protected def loadCancelledJobs: Seq[DocumentSetCreationJob] =
    for ((ds, fg) <- cancelledJobs)
      yield DocumentSetCreationJob(jobType = FileUpload, state = Cancelled, documentSetId = ds, fileGroupId = Some(fg))

}

