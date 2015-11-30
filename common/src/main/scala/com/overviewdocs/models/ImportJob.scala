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

case class CloneImportJob(cloneJob: CloneJob) extends ImportJob {
  override val documentSetId = cloneJob.destinationDocumentSetId

  override val progress = (cloneJob.cancelled, cloneJob.stepNumber) match {
    case (true, _) => None
    case (_, 0) => Some(0.0) // start
    case (_, 1) => Some(0.4) // copied documents
    case (_, 2) => Some(0.5) // indexed documents
    case (_, 3) => Some(0.55) // copied document_processing_errors
    case (_, 4) => Some(0.7) // copied tags
    case (_, 5) => Some(1.0) // copied trees
    case _ => None
  }

  override val description = Some((cloneJob.cancelled, cloneJob.stepNumber) match {
    case (true, _) | (_, 5) => ("models.CloneImportJob.cleaning", Seq())
    case _ => ("models.CloneImportJob.processing", Seq())
  })

  override val estimatedCompletionTime = None
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

case class DocumentCloudImportJob(documentCloudImport: DocumentCloudImport) extends ImportJob {
  override val documentSetId = documentCloudImport.documentSetId

  private def dci = documentCloudImport
  private def desc(key: String, args: Any*) = (s"models.DocumentCloudImportJob.$key", args)

  override val progress = (dci.nIdListsFetched, dci.nIdListsTotal, dci.nFetched, dci.nTotal) match {
    case (_, None, _, _) => None
    case (num, Some(denom), _, None) if denom > 0 => Some(0.05 * num / denom)
    case (_, _, num, Some(denom)) if denom > 0 => Some(0.05 + 0.95 * num / denom)
    case (_, _, _, _) => None
  }

  override val description = (dci.nIdListsFetched, dci.nIdListsTotal, dci.nFetched, dci.nTotal) match {
    case (_, None, _, _) => Some(desc("starting"))
    case (_, _, _, None) => Some(desc("listing"))
    case (_, _, a, Some(b)) if a == b => Some(desc("cleaning"))
    case (_, _, a, Some(b)) => Some(desc("copying", a, b))
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
