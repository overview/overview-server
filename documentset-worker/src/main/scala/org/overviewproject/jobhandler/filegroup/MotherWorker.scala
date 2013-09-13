package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ListenForFileGroupJobs
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload
import org.overviewproject.tree.orm.DocumentSetCreationJobState.{ NotStarted, Preparing }
import org.overviewproject.tree.orm.FileJobState._

object MotherWorkerProtocol {
  sealed trait Command
  case class StartClusteringCommand(
    fileGroupId: Long,
    title: String,
    lang: String,
    suppliedStopWords: String) extends Command
}

trait FileGroupJobHandlerComponent {
  def createFileGroupJobHandler: Props
  val storage: Storage

  trait Storage {
    def findFileGroup(fileGroupId: Long): Option[FileGroup]
    def countFileUploads(fileGroupId: Long): Int
    def countProcessedFiles(fileGroupId: Long): Int
    def storeDocumentSet(title: String, lang: String, suppliedStopWords: String): Long
    def storeDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, state: DocumentSetCreationJobState.Value, lang: String, suppliedStopWords: String): Long

  }
}

class MotherWorker extends Actor {
  this: FileGroupJobHandlerComponent =>

  import MotherWorkerProtocol._

  private val fileGroupJobHandlers: Seq[ActorRef] = for (i <- 1 to 2) yield {
    val handler = context.actorOf(createFileGroupJobHandler)
    handler ! ListenForFileGroupJobs

    handler
  }

  def receive = {
    case StartClusteringCommand(fileGroupId, title, lang, suppliedStopWords) =>
      storage.findFileGroup(fileGroupId).map { fileGroup =>
        val documentSetId = storage.storeDocumentSet(title, lang, suppliedStopWords)
        val jobState = computeJobState(fileGroup)
        storage.storeDocumentSetCreationJob(documentSetId, fileGroupId, jobState, lang, suppliedStopWords)
      }

  }

  /**
   * If all files have been uploaded, and all uploaded files have been processed,
   * the documentSetCreationJob state is `NotStarted` (ready for clustering). Otherwise the state
   * is `Preparing`
   */
  private def computeJobState(fileGroup: FileGroup): DocumentSetCreationJobState.Value =
    if ((fileGroup.state == Complete) &&
      (storage.countFileUploads(fileGroup.id) == storage.countProcessedFiles(fileGroup.id))) NotStarted
    else Preparing

}