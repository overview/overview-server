package org.overviewproject.models

import java.sql.Timestamp

import org.overviewproject.tree.orm.{DocumentSet => DeprecatedDocumentSet}

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
) {
  def toDeprecatedDocumentSet = DeprecatedDocumentSet(
    id,
    title,
    query,
    public,
    createdAt,
    documentCount,
    documentProcessingErrorCount,
    importOverflowCount,
    uploadedFileId,
    version,
    deleted
  )
}
