package org.overviewproject.models

case class DocumentProcessingError(
  id: Long,
  documentSetId: Long,
  textUrl: String,
  message: String,
  statusCode: Option[Int],
  headers: Option[String])