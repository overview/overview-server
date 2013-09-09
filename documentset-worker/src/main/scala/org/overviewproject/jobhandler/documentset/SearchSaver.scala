package org.overviewproject.jobhandler.documentset

import akka.actor.Actor

import org.overviewproject.database.Database
import org.overviewproject.database.orm.stores.DocumentSearchResultStore
import org.overviewproject.documentcloud.Document
import org.overviewproject.tree.orm.DocumentSearchResult

object SearchSaverProtocol {
  case class Save(searchResultId: Long, documentSetId: Long, documents: Iterable[Document])
  case class SaveIds(searchResultId: Long, documentIds: Iterable[Long])
}

trait SearchSaverComponents {
  val storage: Storage

  class Storage {

    def storeDocuments(searchId: Long, documentIds: Iterable[Long]): Unit = {
      Database.inTransaction {
        val documentSearchResults = documentIds.map(docId => DocumentSearchResult(docId, searchId))

        DocumentSearchResultStore.insertBatch(documentSearchResults)
      }
    }
  }
}

class SearchSaver extends Actor {
  this: SearchSaverComponents =>

  import SearchSaverProtocol._

  def receive = {
    case SaveIds(searchId, documentIds) => storage.storeDocuments(searchId, documentIds)
  }
}

trait ActualSearchSaverComponents extends SearchSaverComponents {
  override val storage: Storage = new Storage()
}