package org.overviewproject.jobhandler.filegroup

import akka.actor.{ Actor, ActorRef }
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.ClusterFileGroupCommand

object ClusteringJobQueueProtocol {
  case class ClusterDocumentSet(documentSetId: Long)
}


/**
 * The `FileGroupJobManager` receives a request from the server to extract text from uploaded files
 * and cluster them. It creates the needed sub-tasks and request that each task be performed by a specific
 * job queue, in sequence.
 */
trait FileGroupJobManager extends Actor {
  import FileGroupJobQueueProtocol._
  import ClusteringJobQueueProtocol._

  protected val fileGroupJobQueue: ActorRef
  protected val clusteringJobQueue: ActorRef
  
  protected val storage: Storage

  trait Storage {
    def createDocumentSetWithJob(fileGroupId: Long, lang: String,
                                 suppliedStopWords: String, importantWords: String): Long

  }

  def receive = {

    case ClusterFileGroupCommand(fileGroupId, name, lang, stopWords, importantWords) => {
      val documentSetId = storage.createDocumentSetWithJob(fileGroupId, lang, stopWords, importantWords)

      fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)
    }
    
    case FileGroupDocumentsCreated(documentSetId) =>
      clusteringJobQueue ! ClusterDocumentSet(documentSetId)

  }

}