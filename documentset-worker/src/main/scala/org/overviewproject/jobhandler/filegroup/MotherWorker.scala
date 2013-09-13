package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ListenForFileGroupJobs
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload
import org.overviewproject.tree.orm.DocumentSetCreationJobState.Preparing

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
    def findFileGroup(fileGroupId: Long): FileGroup
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
    case StartClusteringCommand(fileGroupId, title, lang, suppliedStopWords) => {
      val documentSetId = storage.storeDocumentSet(title, lang, suppliedStopWords)
      
      storage.storeDocumentSetCreationJob(documentSetId, fileGroupId, Preparing, lang, suppliedStopWords)
    }
  }
}