package org.overviewproject.models

/** A user-uploaded file that is the source of documents.
  *
  * We reference-count these files to save storage. Clone document sets
  * increase the reference count rather than copying the file.
  */
case class File(
  id: Long,
  referenceCount: Int,
  name: String,
  contentsLocation: String,
  contentsSize: Long,
  viewLocation: String,
  viewSize: Long
)

