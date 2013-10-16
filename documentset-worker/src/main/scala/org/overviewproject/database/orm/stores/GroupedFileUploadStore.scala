package org.overviewproject.database.orm.stores

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.stores.BaseStore
import org.squeryl.Query
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.FinderResult

object GroupedFileUploadStore extends BaseStore(Schema.groupedFileUploads) {

  def deleteLargeObjectsInFileGroup(fileGroupId: Long): Unit = {
    val query = from(Schema.groupedFileUploads)(u =>
      where(u.fileGroupId === fileGroupId)
        select (&(lo_unlink(Some(u.contentsOid)))))

    query.headOption.get // force execution
  }

  def deleteByFileGroup(fileGroupId: Long) = {
    val query = from(Schema.groupedFileUploads)(u =>
      where(u.fileGroupId === fileGroupId)
        select (u))

    delete(query)
  }
}