package org.overviewproject.jobhandler

import akka.actor.Actor

object DeleteHandlerProtocol {
  case class DeleteDocumentSet(documentSetId: Long)
}

trait DeleteHandler extends Actor {

  def receive = {
    case _ =>
  }
}