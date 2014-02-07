package org.overviewproject.reclustering

import org.overviewproject.tree.orm.Document
import org.overviewproject.database.Database
import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.finders.ResultPage

trait PagedDocumentFinder {
  def findDocuments(page: Int): Iterable[Document]
  def numberOfDocuments: Long
}

object PagedDocumentFinder {
  def apply(documentSetId: Long, pageSize: Int): PagedDocumentFinder =
    new PagedDocumentFinder {
    
    val query = DocumentFinder.byDocumentSet(documentSetId)
      override def findDocuments(page: Int): Iterable[Document] = Database.inTransaction {
        val query = DocumentFinder.byDocumentSet(documentSetId).orderedById
        ResultPage(query, pageSize, page)
      }
      
      override def numberOfDocuments: Long = Database.inTransaction {
        DocumentFinder.byDocumentSet(documentSetId).count
      }

    }
}