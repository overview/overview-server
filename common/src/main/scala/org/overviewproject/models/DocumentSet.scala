package org.overviewproject.models

import java.sql.Timestamp

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
  version: Int,
  deleted: Boolean
)