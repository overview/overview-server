package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema.{ documents, pages, tempDocumentSetFiles }
import org.squeryl.Query
import org.overviewproject.tree.orm.Page

object PageStore extends BaseStore(pages) {

  def removeReferenceByFile(fileIds: Seq[Long]): Unit = {
    val pagesToUpdate = from(pages)(p =>
      where(p.fileId in fileIds)
        select (p)
        orderBy (p.id)).forUpdate

    pages.update(pagesToUpdate.map(p => p.copy(referenceCount = p.referenceCount - 1)))

    val pagesToDelete = from(pages)(p =>
      where(p.fileId in fileIds and p.referenceCount === 0)
        select (p))

    pages.delete(pagesToDelete)
  }

  // FIXME: Pick one of these methods. Reading all fileIds to call the first one is probably
  // less efficient than making the same query twice, as below
  def removeReferenceByTempDocumentSet(documentSetId: Long): Unit = {
    val fileIdQuery = from(tempDocumentSetFiles)(dsf =>
      where(dsf.documentSetId === documentSetId)
        select (dsf.fileId))

    val pagesToUpdateQuery = from(pages)(p =>
      where(p.fileId in fileIdQuery)
        select (p)
        orderBy (p.id)).forUpdate

    pages.update(pagesToUpdateQuery.map(p => p.copy(referenceCount = p.referenceCount - 1)))

    val pagesToDelete = from(pages)(p =>
      where(p.fileId in fileIdQuery and p.referenceCount === 0)
        select (p))

    pages.delete(pagesToDelete)

  }
}