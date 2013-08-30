package org.overviewproject.jobhandler

import akka.actor.Actor

object FileHandlerProtocol {
  case class ExtractText(documentSetId: Long, fileId: Long)
}