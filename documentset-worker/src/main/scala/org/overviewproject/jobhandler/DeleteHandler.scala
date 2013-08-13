package org.overviewproject.jobhandler

import scala.language.postfixOps
import akka.actor.Actor
import org.overviewproject.jobhandler.JobHandlerProtocol.JobDone
import scala.util.{ Failure, Success }
import org.overviewproject.util.Logger

object DeleteHandlerProtocol {
  case class DeleteDocumentSet(documentSetId: Long)
}


trait DeleteHandler extends Actor with SearcherComponents {
 import DeleteHandlerProtocol._
 import context.dispatcher
 
  def receive = {
    case DeleteDocumentSet(documentSetId) => {
      val aliasResponse = searchIndex.deleteDocumentSetAlias(documentSetId)
      val documentsResponse = searchIndex.deleteDocuments(documentSetId) 
      
      val combinedResponse = for {
        a <- aliasResponse
        d <- documentsResponse
      } yield d
      
      combinedResponse onComplete {
        case Success(r) => {
          context.parent ! JobDone
          context.stop(self)
        }
        case Failure(t) => {
          Logger.error("Deleting indexed documents failed", t)
          context.parent ! JobDone
          context.stop(self)
        }

      }
    }
  }
}