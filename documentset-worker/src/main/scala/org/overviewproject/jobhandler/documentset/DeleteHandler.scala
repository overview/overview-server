package org.overviewproject.jobhandler.documentset

import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.actor.Actor

import org.overviewproject.jobhandler.JobDone
import org.overviewproject.util.Logger


object DeleteHandlerProtocol {
  case class DeleteDocumentSet(documentSetId: Long)
}

trait DeleteHandler extends Actor with SearcherComponents {
  import DeleteHandlerProtocol._
  import context.dispatcher

  def receive = {
    case DeleteDocumentSet(documentSetId) => {

      // delete alias first, so no new documents can be inserted.
      // creating futures inside for comprehension ensures the calls
      // are run sequentially
      val combinedResponse = for {
        aliasResponse <- searchIndex.deleteDocumentSetAlias(documentSetId)
        documentsResponse <- searchIndex.deleteDocuments(documentSetId)
      } yield documentsResponse

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