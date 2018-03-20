package com.overviewdocs.ingest.model

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
  fileGroupJob: ResumedFileGroupJob, // for cancel, ingest and post-ingest
  progressPiece: ProgressPiece,      // for progress (and progress of children)
  rootId: Option[Long],              // to create children while processing
  parentId: Option[Long],            // to create children while processing
  filename: String,                  // for processing
  contentType: String,               // for processing
  languageCode: String,              // for processing
  metadata: JsObject,                // to create children while processing
  wantOcr: Boolean,                  // for processing
  wantSplitByPage: Boolean,          // for processing
  blob: BlobStorageRefWithSha1       // for processing, and to let children inherit
) {
  def blobLocation: String = blob.location
  def blobNBytes: Int = blob.nBytes
  def blobSha1: Array[Byte] = blob.sha1

  def isCanceled: Boolean = fileGroupJob.isCanceled
}
