package org.overviewproject.reclustering

import org.overviewproject.tree.orm.Document
import org.overviewproject.database.Database
import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.finders.ResultPage
import org.overviewproject.util.Logger

trait PagedDocumentFinder {
  def findDocuments(page: Int): Iterable[Document]
  def numberOfDocuments: Long
}

object PagedDocumentFinder {
  def apply(documentSetId: Long, pageSize: Int): PagedDocumentFinder =
    new PagedDocumentFinder {

      override def findDocuments(page: Int): Seq[Document] = Database.inTransaction {
        val query = DocumentFinder.byDocumentSet(documentSetId).orderedById
        val d = ResultPage(query, pageSize, page).toSeq
        d.size
        d
      }

      override def numberOfDocuments: Long = Database.inTransaction {
        DocumentFinder.byDocumentSet(documentSetId).count
      }

    }
}