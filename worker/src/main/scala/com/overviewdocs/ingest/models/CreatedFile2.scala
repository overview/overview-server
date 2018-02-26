package com.overviewdocs.ingest.models

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
  documentSetId: Long,                    // for ingest, later
  rootId: Option[Long],                   // to create children while processing
  parentId: Option[Long],                 // to create children while processing
  indexInParent: Int,                     // to delete, in case of processing error
  metadata: File2.Metadata,               // to create children while processing
  pipelineOptions: File2.PipelineOptions, // for processing
  blobOpt: Option[BlobStorageRef]         // for processing
) {
  def asWrittenFile2Opt: Option[WrittenFile2] = blobOpt match {
    case Some(blob) => Some(WrittenFile2(id, documentSetId, rootId, parentId, metadata, pipelineOptions, blob))
    case None => None
  }
}
