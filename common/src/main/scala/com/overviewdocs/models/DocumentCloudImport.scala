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

  /** Number of Documents+DocumentProcessingErrors we have written to the
    * database.
    *
    * If `splitPages` is true, there are more Documents.
    */
  nFetched: Int,

  /** Number of Documents we need to write to the database.
    *
    * If `splitPages` is true, this is the number of pages we need to fetch.
    * Otherwise, it's the number of documents.
    */
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
