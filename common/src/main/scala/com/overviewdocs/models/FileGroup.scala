package com.overviewdocs.models

import java.time.Instant
import play.api.libs.json.JsObject

case class FileGroup(
  id: Long,
  userEmail: String,
  apiToken: Option[String],
  deleted: Boolean,
  addToDocumentSetId: Option[Long],
  lang: Option[String],
  splitDocuments: Option[Boolean],
  nFiles: Option[Int],
  nBytes: Option[Long],
  nFilesProcessed: Option[Int],
  nBytesProcessed: Option[Long],
  estimatedCompletionTime: Option[Instant],
  metadataJson: JsObject
)

object FileGroup {
  case class CreateAttributes(
    userEmail: String,
    apiToken: Option[String]
  )
}
