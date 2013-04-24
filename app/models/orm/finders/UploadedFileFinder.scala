package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.UploadedFile
import models.orm.Schema

object UploadedFileFinder extends Finder {
  /** Returns the `UploadedFile`s with the given id.
    *
    * This can have 0 or 1 row.
    */
  def byUploadedFile(uploadedFile: Long) : FinderResult[UploadedFile] = {
    Schema.uploadedFiles.where(_.id === uploadedFile)
  }

  /** Returns the `UploadedFile`s from the given DocumentSet.
    *
    * This can have 0 or 1 row.
    */
  def byDocumentSet(documentSet: Long) : FinderResult[UploadedFile] = {
    // Don't use a join(): it breaks Squeryl's delete()
    val uploadedFileIds = from(Schema.documentSets)(ds =>
      where(ds.id === documentSet)
      select(ds.uploadedFileId)
    )

    Schema.uploadedFiles.where(_.id in uploadedFileIds)
  }
}
