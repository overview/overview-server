package org.overviewproject.reclustering

import org.overviewproject.tree.orm.Document
import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.finders.ResultPage
import org.overviewproject.util.Logger

trait PagedDocumentFinder {
  def findDocuments(page: Int): Seq[Document]
  def numberOfDocuments: Long
}

object PagedDocumentFinder {
  def apply(documentSetId: Long, tagId: Option[Long], pageSize: Int): PagedDocumentFinder =
    new PagedDocumentFinder {

      private val query = tagId.map {
        t => DocumentFinder.byDocumentSetAndTag(documentSetId, t).orderedById
      }
      .getOrElse { DocumentFinder.byDocumentSet(documentSetId).orderedById }

      override def findDocuments(page: Int): Seq[Document] = DeprecatedDatabase.inTransaction {
        val d = ResultPage(query, pageSize, page).toSeq
        d.size
        d
      }

      override def numberOfDocuments: Long = DeprecatedDatabase.inTransaction {
       query.count
      }

    }
}
