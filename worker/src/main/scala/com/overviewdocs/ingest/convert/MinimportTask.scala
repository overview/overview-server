package com.overviewdocs.ingest.convert

import java.time.Instant
import java.util.UUID

import com.overviewdocs.models.File2

case class MinimportTask(
  file2: File2,
  uuid: UUID,
  queuedAt: Instant,
  startedAt: Option[Instant],
  lastActivityAt: Option[Instant]
)
