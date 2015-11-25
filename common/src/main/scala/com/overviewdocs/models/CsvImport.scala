package com.overviewdocs.models

import java.nio.charset.Charset
import java.time.Instant

case class CsvImport(
  id: Long,
  documentSetId: Long,
  filename: String,
  charsetName: String, // Charset isn't serializable
  lang: String,
  loid: Long,
  nBytes: Long,
  nBytesProcessed: Long,
  nDocuments: Int,
  cancelled: Boolean,
  estimatedCompletionTime: Option[Instant],
  createdAt: Instant
) {
  def charset: Charset = Charset.forName(charsetName)
}

object CsvImport {
  case class CreateAttributes(
    documentSetId: Long,
    filename: String,
    charsetName: String,
    lang: String,
    loid: Long,
    nBytes: Long,
    nBytesProcessed: Long = 0L,
    nDocuments: Int = 0,
    cancelled: Boolean = false,
    estimatedCompletionTime: Option[Instant] = None,
    createdAt: Instant = Instant.now
  )
}
