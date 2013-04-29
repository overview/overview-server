package models

case class DocumentCloudImportJob(
  ownerEmail: String,
  title: String,
  projectId: Long,
  credentials: Option[DocumentCloudCredentials],
  splitDocuments: Boolean
)
