package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema._
import org.overviewproject.tree.orm.UploadedFile
import org.overviewproject.tree.orm.finders.BaseUploadedFileFinder
import org.overviewproject.tree.orm.finders.FinderResult


object UploadedFileFinder extends BaseUploadedFileFinder(uploadedFiles, documentSets) {

  def byDocumentSet(documentSet: Long) : FinderResult[UploadedFile] = byDocumentSetQuery(documentSet)
}