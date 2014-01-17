package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema.{ documents, files }
import org.squeryl.Query
import org.overviewproject.tree.orm.File
import org.overviewproject.util.Logger

object FileStore extends BaseStore(files) {

  def deleteLargeObjectsByDocumentSet(documentSetId: Long): Unit =
    from(files, documents)((f, d) =>
      where(d.documentSetId === documentSetId and d.fileId === f.id)
        select (&(lo_unlink(Some(f.contentsOid))))).toIterable

  def removeReference(fileIds: Iterable[Long]): Unit = {
    val filesToUpdate = from(files)(f =>
      where(f.id in fileIds)
        select (f)
        orderBy (f.id)).forUpdate

    Logger.debug("Updating ref count")
    files.update(filesToUpdate.map(f => f.copy(referenceCount = f.referenceCount - 1)))
    
    Logger.debug("deleting content")
    val deleteContents = from(files)(f =>
      where(f.id in fileIds and f.referenceCount === 0)
      select(&(lo_unlink(Some(f.contentsOid))))).toIterable

    Logger.debug("deleting files")
    val filesToDelete = from(files)(f =>
      where(f.id in fileIds and f.referenceCount === 0)
      select(f))


    files.delete(filesToDelete)
    Logger.debug("delete done")
  }

}
