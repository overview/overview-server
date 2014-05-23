package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema.{ documents, files, tempDocumentSetFiles }
import org.squeryl.Query
import org.overviewproject.tree.orm.File

object FileStore extends BaseStore(files) {

  def deleteLargeObjectsByDocumentSet(documentSetId: Long): Unit =
    from(files, documents)((f, d) =>
      where(d.documentSetId === documentSetId and d.fileId === f.id)
        select (&(lo_unlink(Some(f.contentsOid))))).toIterable

  def removeReference(fileIds: Seq[Long]): Unit = {
    val filesToUpdate = from(files)(f =>
      where(f.id in fileIds)
        select (f)
        orderBy (f.id)).forUpdate

    files.update(filesToUpdate.map(f => f.copy(referenceCount = f.referenceCount - 1)))

    val deleteContents = from(files)(f =>
      where(f.id in fileIds and f.referenceCount === 0)
        select (&(lo_unlink(Some(f.contentsOid))))).toIterable

    val filesToDelete = from(files)(f =>
      where(f.id in fileIds and f.referenceCount === 0)
        select (f))

    files.delete(filesToDelete)
  }

  def removeReferenceByTempDocumentSet(documentSetId: Long): Unit = {
    val fileIdQuery = from(tempDocumentSetFiles)(dsf =>
      where(dsf.documentSetId === documentSetId)
        select (dsf.fileId))
        
    val filesToUpdateQuery = from(files)(f =>
      where (f.id in fileIdQuery)
      select (f)
      orderBy (f.id)).forUpdate

    files.update(filesToUpdateQuery.map(f => f.copy(referenceCount = f.referenceCount - 1)))
    
    val deleteContents = from(files)(f =>
      where(f.id in fileIdQuery and f.referenceCount === 0)
        select (&(lo_unlink(Some(f.contentsOid))))).toIterable

    val filesToDelete = from(files)(f =>
      where(f.id in fileIdQuery and f.referenceCount === 0)
        select (f))

    files.delete(filesToDelete)
    
        
  }

}
