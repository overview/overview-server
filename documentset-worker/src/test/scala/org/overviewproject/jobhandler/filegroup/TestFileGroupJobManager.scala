package com.overviewdocs.jobhandler.filegroup

import akka.actor._
import com.overviewdocs.jobhandler.filegroup.FileGroupJobMessages._
import com.overviewdocs.tree.orm.DocumentSetCreationJob
import com.overviewdocs.tree.DocumentSetCreationJobType._
import com.overviewdocs.tree.orm.DocumentSetCreationJobState._
import com.overviewdocs.test.ParameterStore
import com.overviewdocs.jobhandler.filegroup.task.UploadProcessOptions

trait JobParameters {
  protected val documentSetId = 1l
  protected val fileGroupId = 2l
  protected val title = "title"
  protected val lang = "en"
  protected val options = UploadProcessOptions("en", false)
  protected val importantWords = "important words"
  protected val suppliedStopWords = "stop words"

  protected val clusterCommand =
    ClusterFileGroupCommand(documentSetId, fileGroupId, title, options.lang, options.splitDocument, suppliedStopWords, importantWords)
  protected val cancelCommand = CancelClusterFileGroupCommand(documentSetId, fileGroupId)

}

trait StorageMonitor extends JobParameters {
  self: TestFileGroupJobManager =>

  
  val updateJobStateFn = ParameterStore[Long]
  val increaseRetryAttemptFn = ParameterStore[DocumentSetCreationJob]
  val failJobFn = ParameterStore[DocumentSetCreationJob]
  
  class MockStorage extends FileGroupJobManager.Storage {
    override def findValidInProgressUploadJobs: Iterable[DocumentSetCreationJob] = loadInterruptedJobs
    override def findValidCancelledUploadJobs: Iterable[DocumentSetCreationJob] = loadCancelledJobs

    override def updateJobState(documentSetId: Long): Option[DocumentSetCreationJob] = {
      updateJobStateFn.store(documentSetId)
      produceUploadJob
    }
    
    override def failJob(job: DocumentSetCreationJob): Unit = failJobFn.store(job)
    
    override def increaseRetryAttempts(job: DocumentSetCreationJob): Unit = 
      increaseRetryAttemptFn.store(job)
  }

  override protected val storage = new MockStorage
}

class TestFileGroupJobManager(
    override protected val fileGroupJobQueue: ActorRef,
    override protected val clusteringJobQueue: ActorRef,
    uploadJobProducer: => Option[DocumentSetCreationJob],
    interruptedJobs: Seq[(Long, Long, Int)],
    cancelledJobs: Seq[(Long, Long)]) extends FileGroupJobManager with StorageMonitor {

  protected def produceUploadJob = uploadJobProducer
  
  protected def loadInterruptedJobs: Seq[DocumentSetCreationJob] =
    for ((ds, fg, n) <- interruptedJobs)
      yield DocumentSetCreationJob(jobType = FileUpload, state = InProgress, 
          documentSetId = ds, fileGroupId = Some(fg), retryAttempts = n)

  protected def loadCancelledJobs: Seq[DocumentSetCreationJob] =
    for ((ds, fg) <- cancelledJobs)
      yield DocumentSetCreationJob(jobType = FileUpload, state = Cancelled, documentSetId = ds, fileGroupId = Some(fg))

}

