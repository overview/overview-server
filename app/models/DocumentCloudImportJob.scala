package models

case class DocumentCloudImportJob(
  ownerEmail: String,
  title: String,
  query: String,
  lang: String,
  credentials: Option[DocumentCloudCredentials],
  splitDocuments: Boolean,
  suppliedStopWords: String,
  importantWords: String
)
