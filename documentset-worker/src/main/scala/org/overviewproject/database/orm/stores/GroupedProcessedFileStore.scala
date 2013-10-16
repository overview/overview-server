package org.overviewproject.database.orm.stores

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.GroupedProcessedFile

object GroupedProcessedFileStore extends BaseStore(Schema.groupedProcessedFiles) {

  def deleteByFileGroup(fileGroupId: Long): Int = {
    val query = from(Schema.groupedProcessedFiles)(f =>
      where(f.fileGroupId === fileGroupId)
        select (f))

    delete(query)
  }
}