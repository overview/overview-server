package org.overviewproject.jobhandler

import akka.actor.Actor
import org.overviewproject.documentcloud.Document

object SearchSaverProtocol {
  case class Save(documents: Iterable[Document])
}
class SearchSaver extends Actor {
  
  def receive = {
    case _ =>
  }
}