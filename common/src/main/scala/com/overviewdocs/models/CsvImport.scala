package com.overviewdocs.models

import java.nio.charset.Charset
import java.time.Instant

case class CsvImport(
  id: Long,
  documentSetId: Long,
  filename: String,
  charset: Charset,
  lang: String,
  loid: Option[Long],
  nBytes: Long,
  nBytesProcessed: Long,
  nDocuments: Int,
  cancelled: Boolean,
  estimatedCompletionTime: Option[Instant],
  createdAt: Instant
)

object CsvImport {
  case class CreateAttributes(
    documentSetId: Long,
    filename: String,
    charset: Charset,
    lang: String,
    loid: Option[Long],
    nBytes: Long,
    nBytesProcessed: Long = 0L,
    nDocuments: Int = 0,
    cancelled: Boolean = false,
    estimatedCompletionTime: Option[Instant] = None,
    createdAt: Instant = Instant.now
  )
}
