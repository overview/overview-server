package com.overviewdocs.models

import java.time.Instant

case class CloneJob(
  id: Int,
  sourceDocumentSetId: Long,
  destinationDocumentSetId: Long,
  stepNumber: Short,
  cancelled: Boolean,
  createdAt: Instant
)

object CloneJob {
  case class CreateAttributes(
    sourceDocumentSetId: Long,
    destinationDocumentSetId: Long,
    stepNumber: Short = 0.toShort,
    cancelled: Boolean = false,
    createdAt: Instant = Instant.now
  )
}
