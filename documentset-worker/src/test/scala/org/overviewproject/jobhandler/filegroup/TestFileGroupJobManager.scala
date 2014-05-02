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
  self: TestFileGroupJobManager =>

  class MockStorage extends Storage {
    override def findInProgressJobInformation: Iterable[(Long, Long)] = loadInterruptedJobs
  }

  override protected val storage = new MockStorage
}

class TestFileGroupJobManager(
  override protected val fileGroupJobQueue: ActorRef,
  override protected val clusteringJobQueue: ActorRef,
  interruptedJobs: Seq[(Long, Long)]) extends FileGroupJobManager with StorageMonitor {
  
  protected def loadInterruptedJobs: Seq[(Long, Long)] = interruptedJobs
}

