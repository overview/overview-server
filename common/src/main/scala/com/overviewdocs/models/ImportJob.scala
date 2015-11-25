package com.overviewdocs.models

import java.time.Instant

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

  /** When we'll be done.
    *
    * If None, we don't have an estimate.
    */
  val estimatedCompletionTime: Option[Instant]
}

case class CsvImportJob(csvImport: CsvImport) extends ImportJob {
  override val documentSetId = csvImport.documentSetId

  override val progress = {
    if (csvImport.nBytes == 0) {
      Some(1.0)
    } else {
      Some(csvImport.nBytesProcessed.toDouble / csvImport.nBytes)
    }
  }

  override val description = progress match {
    case Some(0.0) => Some(("models.CsvImportJob.starting", Seq()))
    case Some(1.0) => Some(("models.CsvImportJob.cleaning", Seq()))
    case _ => Some(("models.CsvImportJob.processing", Seq(csvImport.nBytesProcessed, csvImport.nBytes)))
  }

  override val estimatedCompletionTime = csvImport.estimatedCompletionTime
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
  override val estimatedCompletionTime = None
}

case class FileGroupImportJob(fileGroup: FileGroup) extends ImportJob {
  override val documentSetId = fileGroup.addToDocumentSetId.get

  override val progress = {
    (fileGroup.deleted, fileGroup.nBytesProcessed, fileGroup.nBytes) match {
      case (_, _, Some(0L)) => None
      case (false, Some(a), Some(b)) => Some(a.toDouble / b)
      case _ => None
    }
  }

  override val description = {
    (fileGroup.deleted, fileGroup.nBytesProcessed, fileGroup.nBytes, fileGroup.nFilesProcessed) match {
      case (true, _, _, _) => Some(("models.FileGroupImportJob.cleaning", Seq()))
      case (_, Some(a), Some(b), _) if a == b => Some(("models.FileGroupImportJob.cleaning", Seq()))
      case (_, Some(0L), _, _) => Some(("models.FileGroupImportJob.starting", Seq()))
      case (_, Some(a), Some(b), Some(c)) if b != 0L => Some(("models.FileGroupImportJob.processing", Seq(a, b, c)))
      case _ => None
    }
  }

  override val estimatedCompletionTime = fileGroup.estimatedCompletionTime
}
