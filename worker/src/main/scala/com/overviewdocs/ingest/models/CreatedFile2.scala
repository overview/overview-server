package com.overviewdocs.ingest.models

import scala.concurrent.Future

import com.overviewdocs.models.{BlobStorageRef,File2}

/** File2: we know it has been Created, but nothing else.
  *
  * This holds just enough information to:
  *
  * 1. Convert to a WrittenFile2 during resume.
  * 2. Convert to a WrittenFile2 while writing to the database.
  */
case class CreatedFile2(
  id: Long,
  fileGroupJob: ResumedFileGroupJob,       // for progress, cancel, ingest and post-ingest
  onProgress: Double => Unit,              // for progress
  rootId: Option[Long],                    // to create children while processing
  parentId: Option[Long],                  // to create children while processing
  indexInParent: Int,                      // to delete, in case of processing error
  filename: String,                        // for processing
  contentType: String,                     // for processing
  languageCode: String,                    // for processing
  metadata: File2.Metadata,                // to create children while processing
  pipelineOptions: File2.PipelineOptions,  // for processing
  blobOpt: Option[BlobStorageRefWithSha1], // for processing
  ownsBlob: Boolean,                       // for delete/resume
  thumbnailLocationOpt: Option[String],    // for delete/resume
  ownsThumbnail: Boolean                   // for delete/resume
) {
  def asWrittenFile2Opt: Option[WrittenFile2] = blobOpt match {
    case Some(blob) => Some(WrittenFile2(id, fileGroupJob, onProgress, rootId, parentId, filename, contentType, languageCode, metadata, pipelineOptions, blob))
    case None => None
  }

  def asProcessedFile2(nChildren: Int, nIngestedChildren: Int): ProcessedFile2 = {
    ProcessedFile2(id, fileGroupJob, parentId, nChildren, nIngestedChildren)
  }
}
