package org.overviewproject.jobhandler

import akka.actor.Actor
import org.overviewproject.documentcloud.Document
import org.overviewproject.database.orm.finders.DocumentFinder
import org.overviewproject.database.orm.DocumentSearchResult
import org.overviewproject.database.orm.stores.DocumentSearchResultStore
import org.overviewproject.database.Database

object SearchSaverProtocol {
  case class Save(searchResultId: Long, documentSetId: Long, documents: Iterable[Document])
}

trait SearchSaverComponents {
  val storage: Storage

  class Storage {
    def storeDocuments(searchId: Long, documentSetId: Long, documents: Iterable[Document]): Unit = {
      Database.inTransaction {
        val documentCloudIds = documents.map(_.id)
        val documentSetDocuments = DocumentFinder.byDocumentSetAndDocumentCloudIds(documentSetId, documentCloudIds)

        val documentSearchResults = documentSetDocuments.map(d => DocumentSearchResult(d.id, searchId))

        DocumentSearchResultStore.insertBatch(documentSearchResults)
      }
    }
  }
}

class SearchSaver extends Actor {
  this: SearchSaverComponents =>

  import SearchSaverProtocol._

  def receive = {
    case Save(searchId, documentSetId, documents) => storage.storeDocuments(searchId, documentSetId, documents)
  }
}

trait ActualSearchSaverComponents extends SearchSaverComponents {
  override val storage: Storage = new Storage()
}