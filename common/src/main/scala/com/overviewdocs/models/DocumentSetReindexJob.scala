package com.overviewdocs.models

import java.time.Instant

/** A request to re-index a document set. */
case class DocumentSetReindexJob(
  id: Long,
  documentSetId: Long,
  lastRequestedAt: Instant,
  startedAt: Option[Instant],
  progress: Double
)

object DocumentSetReindexJob {
  case class CreateAttributes(
    documentSetId: Long,
    lastRequestedAt: Instant = Instant.now,
    startedAt: Option[Instant] = None,
    progress: Double = 0.0
  )
}
