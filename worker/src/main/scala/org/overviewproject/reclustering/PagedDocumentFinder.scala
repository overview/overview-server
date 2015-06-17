package org.overviewproject.reclustering

import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.models.Document
import org.overviewproject.models.tables.{DocumentTags,Documents}

trait PagedDocumentFinder {
  def findDocuments(page: Int): Seq[Document]
  def numberOfDocuments: Long
}

object PagedDocumentFinder {
  def apply(documentSetId: Long, maybeTagId: Option[Long], pageSize: Int): PagedDocumentFinder = {
    new PagedDocumentFinder with HasBlockingDatabase {
      import database.api._

      val baseQuery = Documents.filter(_.documentSetId === documentSetId)

      // TODO: use DocumentBackend for this. Sorting is evil, and we need to do it.
      val query = maybeTagId match {
        case None => baseQuery.sortBy(_.id)
        case Some(tagId) => {
          baseQuery
            .filter(_.id in DocumentTags.filter(_.tagId === tagId).map(_.documentId))
            .sortBy(_.id)
        }
      }

      override def findDocuments(page: Int): Seq[Document] = {
        val pageQuery = query
          .drop(pageSize * (page - 1))
          .take(pageSize)
        blockingDatabase.seq(pageQuery)
      }

      override def numberOfDocuments: Long = {
        blockingDatabase.length(query)
      }
    }
  }
}
