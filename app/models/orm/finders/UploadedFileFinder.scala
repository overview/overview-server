package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.UploadedFile
import org.overviewproject.tree.orm.finders.{ BaseUploadedFileFinder, FinderResult }

import models.orm.Schema.{ documentSets, uploadedFiles }

object UploadedFileFinder extends BaseUploadedFileFinder(uploadedFiles, documentSets) {
  /** Returns the `UploadedFile`s with the given id.
    *
    * This can have 0 or 1 row.
    */
  def byUploadedFile(uploadedFile: Long) : FinderResult[UploadedFile] = {
    uploadedFiles.where(_.id === uploadedFile)
  }

  /** Returns the `UploadedFile`s from the given DocumentSet.
    *
    * This can have 0 or 1 row.
    */
  def byDocumentSet(documentSet: Long) : FinderResult[UploadedFile] = byDocumentSetQuery(documentSet) 
    
}
