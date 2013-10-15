package org.overviewproject.database.orm.stores

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.stores.BaseStore
import org.squeryl.Query
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.postgres.SquerylEntrypoint._

object GroupedFileUploadStore extends BaseStore(Schema.groupedFileUploads) {

  def deleteUnprocessedUploadsAndContents(fileGroupId: Long): Int = {
    val query = join(Schema.groupedFileUploads, Schema.groupedProcessedFiles)((u, f) =>
      where(u.fileGroupId === fileGroupId)
        select (u)
        on (u.contentsOid === f.contentsOid))

    from(query)(u =>
      select(&(lo_unlink(Some(u.contentsOid)))))

    delete(query)
  }
}