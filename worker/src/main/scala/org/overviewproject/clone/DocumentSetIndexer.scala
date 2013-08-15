package org.overviewproject.clone

import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.util.SearchIndex
import scala.concurrent.Future

object DocumentSetIndexer {

  def indexDocuments(documentSetId: Long): Future[Unit] = {
    val documents = DocumentFinder.byDocumentSet(documentSetId)
    
    val indexingSession = SearchIndex.startDocumentSetIndexingSession(documentSetId)
    
    documents.foreach(d =>
      indexingSession.indexDocument(documentSetId, d.id, d.text.getOrElse(""), d.title, d.suppliedId)
    )
    indexingSession.complete
   
    indexingSession.requestsComplete
  }
}