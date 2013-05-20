package org.overviewproject.jobhandler

import akka.actor.Actor
import org.overviewproject.documentcloud.Document

object SearchSaverProtocol {
  case class Save(searchResultId: Long, documents: Iterable[Document])
}

trait SearchSaverComponents {
  val storage: Storage
  
  class Storage {
    def storeDocuments(id: Long, documents: Iterable[Document]): Unit = ???
  }
}

class SearchSaver extends Actor {
  this: SearchSaverComponents =>
    
  import SearchSaverProtocol._
  
  def receive = {
    case Save(id, documents) => storage.storeDocuments(id, documents)
  }
}

trait ActualSearchSaverComponents extends SearchSaverComponents {
  override val storage: Storage = new Storage()
}