package com.overviewdocs.models

case class DocumentProcessingError(
  id: Long,
  documentSetId: Long,
  textUrl: String,
  message: String,
  statusCode: Option[Int],
  headers: Option[String]
)

object DocumentProcessingError {
  case class CreateAttributes(
    documentSetId: Long,
    textUrl: String,
    message: String,
    statusCode: Option[Int],
    headers: Option[String]
  )
}
