package com.overviewdocs.ingest.model

import play.api.libs.json.JsObject

/** File2: we know it has been Created, but nothing else.
  *
  * This holds just enough information to:
  *
  * 1. Convert to a WrittenFile2 during resume.
  * 2. Convert to a WrittenFile2 while writing to the database.
  */
case class CreatedFile2(
  id: Long,
  fileGroupJob: ResumedFileGroupJob,       // for cancel, ingest and post-ingest
  progressPiece: ProgressPiece,            // for progress (and progress of children)
  rootId: Option[Long],                    // to create children while processing
  parentId: Option[Long],                  // to create children while processing
  indexInParent: Int,                      // to delete, in case of processing error
  filename: String,                        // for processing
  contentType: String,                     // for processing
  languageCode: String,                    // for processing
  metadata: JsObject,                      // to create children while processing
  wantOcr: Boolean,                        // for processing
  wantSplitByPage: Boolean,                // for processing
  blobOpt: Option[BlobStorageRefWithSha1], // for processing
  ownsBlob: Boolean,                       // for delete/resume
  thumbnailLocationOpt: Option[String],    // for delete/resume
  ownsThumbnail: Boolean                   // for delete/resume
) {
  def asWrittenFile2Opt: Option[WrittenFile2] = blobOpt match {
    case Some(blob) => Some(WrittenFile2(id, fileGroupJob, progressPiece, rootId, parentId, filename, contentType, languageCode, metadata, wantOcr, wantSplitByPage, blob))
    case None => None
  }

  def asProcessedFile2(nChildren: Int, nIngestedChildren: Int): ProcessedFile2 = {
    ProcessedFile2(id, fileGroupJob, parentId, nChildren, nIngestedChildren)
  }
}
