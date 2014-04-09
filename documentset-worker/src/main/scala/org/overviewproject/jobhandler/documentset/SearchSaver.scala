package org.overviewproject.jobhandler.documentset

import akka.actor.Actor
import org.overviewproject.database.Database
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.DocumentSearchResult
import org.overviewproject.tree.orm.stores.BaseStore

object SearchSaverProtocol {
  case class SaveIds(searchResultId: Long, documentIds: Iterable[Long])
}

trait SearchSaverComponents {
  val storage: Storage

  class Storage {

    def storeDocuments(searchId: Long, documentIds: Iterable[Long]): Unit = {
      Database.inTransaction {
        val documentSearchResults = documentIds.map(docId => DocumentSearchResult(docId, searchId))

        BaseStore(Schema.documentSearchResults).insertBatch(documentSearchResults)
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
