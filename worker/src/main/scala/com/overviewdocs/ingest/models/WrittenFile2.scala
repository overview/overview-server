package com.overviewdocs.ingest.models

import play.api.libs.json.JsObject
import scala.concurrent.Future

import com.overviewdocs.models.{BlobStorageRef,File2}

/** File2: we know it has been Written, but nothing else.
  *
  * This holds just enough information to:
  *
  * 1. Convert to a ProcessedFile2 with a simple database read.
  * 2. Convert to a ProcessedFile2 while writing to the database.
  *
  * We use this during ingestion. `documentSetId` will be used at the final
  * "ingest" state change.
  */
case class WrittenFile2(
  id: Long,
  fileGroupJob: ResumedFileGroupJob,      // for cancel, ingest and post-ingest
  onProgress: Double => Unit,             // for progress
  rootId: Option[Long],                   // to create children while processing
  parentId: Option[Long],                 // to create children while processing
  filename: String,                       // for processing
  contentType: String,                    // for processing
  languageCode: String,                   // for processing
  metadata: File2.Metadata,               // to create children while processing
  pipelineOptions: File2.PipelineOptions, // for processing
  blob: BlobStorageRefWithSha1            // for processing, and to let children inherit
)
