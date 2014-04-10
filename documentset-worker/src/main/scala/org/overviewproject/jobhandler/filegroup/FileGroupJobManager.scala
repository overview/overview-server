package org.overviewproject.jobhandler.filegroup

import akka.actor.{ Actor, ActorRef }
import org.overviewproject.jobs.models.ClusterFileGroup

object FileGroupJobQueueProtocol {
  case class CreateDocumentsFromFileGroup(fileGroupId: Long, documentSetId: Long)
}

trait FileGroupJobManager extends Actor {
  import FileGroupJobQueueProtocol._
  
  protected val fileGroupJobQueue: ActorRef
  
  protected val storage: Storage
  
  trait Storage {
    def createDocumentSet(title: String): Long
    def createJob(documentSetId: Long, fileGroupId: Long, lang: String,
                            suppliedStopWords: String, importantWords: String): Unit

  }

  def receive = {

    case ClusterFileGroup(fileGroupId, name, lang, stopWords, importantWords) => { 
      val documentSetId = storage.createDocumentSet(name)
      storage.createJob(documentSetId, fileGroupId, lang, stopWords, importantWords)
      
      fileGroupJobQueue ! CreateDocumentsFromFileGroup(fileGroupId, documentSetId)
    }
      
  }

}