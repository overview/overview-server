package models

case class DocumentCloudImportJob(
  ownerEmail: String,
  title: String,
  projectId: String,
  credentials: Option[DocumentCloudCredentials],
  splitDocuments: Boolean
)
