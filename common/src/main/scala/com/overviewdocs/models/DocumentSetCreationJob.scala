package com.overviewdocs.models

object DocumentSetCreationJobType extends Enumeration {
  type DocumentSetCreationJobType = Value
  
  val DocumentCloud = Value(1)
  val CsvUpload = Value(2)
  val Clone = Value(3)
}

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value
  
  // XXX nix the capitalized stuff. I'm not sure why we need it, but we test for it in app/.
  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
  val Cancelled = Value(3, "CANCELLED")
}

case class DocumentSetCreationJob( 
  id: Long,
  documentSetId: Long,
  jobType: DocumentSetCreationJobType.Value,
  retryAttempts: Int,
  lang: String,
  splitDocuments: Boolean,
  documentcloudUsername: Option[String],
  documentcloudPassword: Option[String],
  contentsOid: Option[Long],
  sourceDocumentSetId: Option[Long],
  state: DocumentSetCreationJobState.Value,
  fractionComplete: Double,
  statusDescription: String,
  canBeCancelled: Boolean
)

object DocumentSetCreationJob {
  case class CreateAttributes(
    documentSetId: Long,
    jobType: DocumentSetCreationJobType.Value,
    retryAttempts: Int,
    lang: String,
    splitDocuments: Boolean,
    documentcloudUsername: Option[String],
    documentcloudPassword: Option[String],
    contentsOid: Option[Long],
    sourceDocumentSetId: Option[Long],
    state: DocumentSetCreationJobState.Value,
    fractionComplete: Double,
    statusDescription: String,
    canBeCancelled: Boolean
  )
}
