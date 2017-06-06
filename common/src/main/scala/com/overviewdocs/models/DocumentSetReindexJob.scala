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
