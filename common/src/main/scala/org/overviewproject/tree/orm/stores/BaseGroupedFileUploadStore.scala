package org.overviewproject.tree.orm.stores


import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.Table
import org.overviewproject.tree.orm.GroupedFileUpload

class BaseGroupedFileUploadStore(groupedFileUploads: Table[GroupedFileUpload]) extends BaseStore(groupedFileUploads) {
 
 def deleteLargeObjectsInFileGroup(fileGroupId: Long): Unit = {
    val query = from(groupedFileUploads)(u =>
      where(u.fileGroupId === fileGroupId)
        select (&(lo_unlink(Some(u.contentsOid)))))

    query.headOption.getOrElse("nothing found") // force execution. getOrElse is needed, in case no uploads are found
  }

  def deleteByFileGroup(fileGroupId: Long) = {
    val query = from(groupedFileUploads)(u =>
      where(u.fileGroupId === fileGroupId)
        select (u))

    delete(query)
  }

}