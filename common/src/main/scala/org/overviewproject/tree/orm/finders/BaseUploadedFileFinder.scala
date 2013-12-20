package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ DocumentSet, UploadedFile }
import org.squeryl.{ Query, Table }

class BaseUploadedFileFinder(table: Table[UploadedFile], documentSetsTable: Table[DocumentSet]) extends Finder {
  
  def byDocumentSetQuery(documentSetId: Long): Query[UploadedFile] = {
    // Don't use a join(): it breaks Squeryl's delete()
    val uploadedFileIds = from(documentSetsTable)(ds =>
      where(ds.id === documentSetId)
      select(ds.uploadedFileId)
    )

    table.where(_.id in uploadedFileIds)
  }

}