package com.overviewdocs.documentcloud

/** Our header information for a DocumentCloud document. */
case class IdListRow(
  documentCloudId: String,
  title: String,
  nPages: Int,
  fullTextUrl: String,
  pageTextUrlTemplate: String,
  access: String
)
