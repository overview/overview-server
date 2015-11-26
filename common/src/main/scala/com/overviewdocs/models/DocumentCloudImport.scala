package com.overviewdocs.models

import java.time.Instant

/** An import job for fetching from DocumentCloud.
  */
case class DocumentCloudImport(
  id: Int,
  documentSetId: Long,
  query: String,
  username: String,
  password: String,
  splitPages: Boolean,
  lang: String,
  nIdListsFetched: Int,
  nIdListsTotal: Option[Int],
  nFetched: Int,
  nTotal: Option[Int],
  cancelled: Boolean,
  createdAt: Instant
)

object DocumentCloudImport {
  case class CreateAttributes(
    documentSetId: Long,
    query: String,
    username: String,
    password: String,
    splitPages: Boolean,
    lang: String,
    nIdListsFetched: Int = 0,
    nIdListsTotal: Option[Int] = None,
    nFetched: Int = 0,
    nTotal: Option[Int] = None,
    cancelled: Boolean = false,
    createdAt: Instant = Instant.now
  )
}
