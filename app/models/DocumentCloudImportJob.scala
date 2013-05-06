package models

case class DocumentCloudImportJob(
  ownerEmail: String,
  title: String,
  query: String,
  credentials: Option[DocumentCloudCredentials],
  splitDocuments: Boolean
)
