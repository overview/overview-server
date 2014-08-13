package org.overviewproject.jobhandler.filegroup

object FileGroupJobMessages {
  sealed trait Command
  case class CancelClusterFileGroupCommand(documentSetId: Long, fileGroupId: Long) extends Command

  case class ClusterFileGroupCommand(
    documentSetId: Long,
    fileGroupId: Long,
    title: String,
    lang: String,
    splitDocuments: Boolean,
    suppliedStopWords: String,
    importantWords: String) extends Command

}