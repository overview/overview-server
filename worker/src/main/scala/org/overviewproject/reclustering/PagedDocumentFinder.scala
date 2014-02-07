package org.overviewproject.reclustering

import org.overviewproject.tree.orm.Document

trait PagedDocumentFinder {
  def findDocuments(documentSetId: Long, page: Int): Iterable[Document]
  def numberOfDocuments(documentSetId: Long): Int
}