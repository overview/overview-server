package com.overviewdocs.models

sealed trait ImportJob {
  /** DocumentSet this job applies to.
    *
    * One DocumentSet may have many jobs applied to it, but only one will be
    * active at any given time.
    */
  val documentSetId: Long

  /** Progress, from 0.0 to 1.0.
    *
    * If None, use a spinner instead of a progress bar.
    */
  val progress: Option[Double]

  /** What we're doing -- as an i18n Messages key.
    *
    * If None, we have no message. TODO remove this possibility?
    */
  val description: Option[(String, Seq[Any])]
}

case class DocumentSetCreationJobImportJob(documentSetCreationJob: DocumentSetCreationJob) extends ImportJob {
  override val documentSetId = documentSetCreationJob.documentSetId
  override val progress = Some(documentSetCreationJob.fractionComplete)
  override val description = {
    val strings = documentSetCreationJob.statusDescription.split(':').toSeq
    strings.headOption match {
      case None => None
      case Some(key) => Some((s"views.ImportJob._documentSetCreationJob.job_state_description.$key", strings.tail))
    }
  }
}

case class FileGroupImportJob(fileGroup: FileGroup) extends ImportJob {
  override val documentSetId = fileGroup.addToDocumentSetId.get
  override val progress = (fileGroup.nBytesProcessed, fileGroup.nBytes) match {
    case (Some(a), Some(b)) if a != 0L => Some(a.toDouble / b)
    case _ => None
  }
  override val description = (fileGroup.nBytesProcessed, fileGroup.nBytes, fileGroup.nFilesProcessed) match {
    case (Some(a), Some(b), Some(c)) if a != 0L => Some(("models.FileGroupImportJob.processing", Seq(a, b, c)))
    case _ => None
  }
}
