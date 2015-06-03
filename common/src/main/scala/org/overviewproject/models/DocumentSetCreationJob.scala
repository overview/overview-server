package org.overviewproject.models

import org.overviewproject.tree.orm.{DocumentSetCreationJob => DeprecatedDocumentSetCreationJob}
import org.overviewproject.tree.{DocumentSetCreationJobType => DeprecatedDocumentSetCreationJobType}
import org.overviewproject.tree.orm.{DocumentSetCreationJobState => DeprecatedDocumentSetCreationJobState}

object DocumentSetCreationJobType extends Enumeration {
  type DocumentSetCreationJobType = Value
  
  val DocumentCloud = Value(1)
  val CsvUpload = Value(2)
  val Clone = Value(3)
  val FileUpload = Value(4)
  val Recluster = Value(5)
}

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value
  
  val NotStarted = Value(0)
  val InProgress = Value(1)
  val Error = Value(2)
  val Cancelled = Value(3)
  val FilesUploaded = Value(4)
  val TextExtractionInProgress = Value(5)
}

case class DocumentSetCreationJob( 
  id: Long,
  documentSetId: Long,
  jobType: DocumentSetCreationJobType.Value,
  retryAttempts: Int,
  lang: String,
  suppliedStopWords: String,
  importantWords: String,
  splitDocuments: Boolean,
  documentcloudUsername: Option[String],
  documentcloudPassword: Option[String],
  contentsOid: Option[Long],
  fileGroupId: Option[Long],
  sourceDocumentSetId: Option[Long],
  treeTitle: Option[String],
  treeDescription: Option[String],
  tagId: Option[Long],
  state: DocumentSetCreationJobState.Value,
  fractionComplete: Double,
  statusDescription: String,
  canBeCancelled: Boolean
) {
  def toDeprecatedDocumentSetCreationJob = DeprecatedDocumentSetCreationJob(
    id,
    documentSetId,
    DeprecatedDocumentSetCreationJobType(jobType.id),
    lang,
    suppliedStopWords,
    importantWords,
    documentcloudUsername,
    documentcloudPassword,
    splitDocuments,
    contentsOid,
    sourceDocumentSetId,
    fileGroupId,
    treeTitle,
    tagId,
    DeprecatedDocumentSetCreationJobState(state.id),
    fractionComplete,
    statusDescription,
    treeDescription,
    retryAttempts,
    canBeCancelled
  )
}

object DocumentSetCreationJob {
  case class CreateAttributes(
    documentSetId: Long,
    jobType: DocumentSetCreationJobType.Value,
    retryAttempts: Int,
    lang: String,
    suppliedStopWords: String,
    importantWords: String,
    splitDocuments: Boolean,
    documentcloudUsername: Option[String],
    documentcloudPassword: Option[String],
    contentsOid: Option[Long],
    fileGroupId: Option[Long],
    sourceDocumentSetId: Option[Long],
    treeTitle: Option[String],
    treeDescription: Option[String],
    tagId: Option[Long],
    state: DocumentSetCreationJobState.Value,
    fractionComplete: Double,
    statusDescription: String,
    canBeCancelled: Boolean
  )
}
