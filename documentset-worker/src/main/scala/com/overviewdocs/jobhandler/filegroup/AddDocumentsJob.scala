package com.overviewdocs.jobhandler.filegroup

import java.util.Locale

import com.overviewdocs.jobhandler.filegroup.task.UploadProcessOptions

/** Represents a `DocumentSetCreationJob`.
  *
  * DocumentSetCreationJob is a terrible table. This class omits all the
  * irrelevant data and gets rid of [[Option]]s.
  */
case class AddDocumentsJob(
  documentSetCreationJobId: Long,
  documentSetId: Long,
  fileGroupId: Long,
  lang: String,
  splitDocuments: Boolean
) {
  def ocrLocales: Seq[Locale] = Seq(new Locale(lang))
}
