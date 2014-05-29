package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema.{ documents, pages, tempDocumentSetFiles }
import org.squeryl.Query
import org.overviewproject.tree.orm.Page

object PageStore extends BaseStore(pages) {

  def removeReferenceByFile(fileIds: Seq[Long]): Unit = {
    val pageIdsToUpdate = from(pages)(p =>
      where(p.fileId in fileIds)
        select (p.id)
        orderBy (p.id)).forUpdate.toSeq

    update(pages)(p =>
      where(p.id in pageIdsToUpdate)
        set (p.referenceCount := p.referenceCount.~ - 1))

    val pagesToDelete = from(pages)(p =>
      where(p.id in pageIdsToUpdate and p.referenceCount === 0)
        select (p))

    pages.delete(pagesToDelete)
  }

  // FIXME: Pick one of these methods. Reading all fileIds to call the first one is probably
  // less efficient than making the same query twice, as below
  def removeReferenceByTempDocumentSet(documentSetId: Long): Unit = {
    val fileIdQuery = from(tempDocumentSetFiles)(dsf =>
      where(dsf.documentSetId === documentSetId)
        select (dsf.fileId))

    val pageIdsToUpdate = from(pages)(p =>
      where(p.fileId in fileIdQuery)
        select (p.id)
        orderBy (p.id)).forUpdate.toSeq

    update(pages)(p =>
      where(p.id in pageIdsToUpdate)
        set (p.referenceCount := p.referenceCount.~ - 1))

    val pagesToDelete = from(pages)(p =>
      where(p.id in pageIdsToUpdate and p.referenceCount === 0)
        select (p))

    pages.delete(pagesToDelete)

  }
}