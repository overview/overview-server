package org.overviewproject.models

import java.sql.Timestamp

import org.overviewproject.metadata.MetadataSchema

case class DocumentSet(
  id: Long,
  title: String,
  query: Option[String],
  public: Boolean,
  createdAt: Timestamp,
  documentCount: Int,
  documentProcessingErrorCount: Int,
  importOverflowCount: Int,
  uploadedFileId: Option[Long],
  metadataSchema: MetadataSchema,
  deleted: Boolean
)

object DocumentSet {
  case class CreateAttributes(
    title: String,
    // For the rest, callers should stick with these default values
    query: Option[String] = None,
    public: Boolean = false,
    createdAt: Timestamp = new Timestamp(System.currentTimeMillis),
    documentCount: Int = 0,
    documentProcessingErrorCount: Int = 0,
    importOverflowCount: Int = 0,
    uploadedFileId: Option[Long] = None,
    metadataSchema: MetadataSchema = MetadataSchema.empty,
    deleted: Boolean = false
  )
}
