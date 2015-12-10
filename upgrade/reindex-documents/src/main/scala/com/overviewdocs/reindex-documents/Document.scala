package com.overviewdocs.upgrade.reindex_documents

case class Document(
  id: Long,
  documentSetId: Long,
  text: String,
  title: String,
  suppliedId: String
)
