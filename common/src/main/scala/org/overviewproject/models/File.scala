package org.overviewproject.models

case class File(
  id: Long,
  referenceCount: Int,
  contentsOid: Long,
  viewOid: Long,
  name: String,
  contentsSize: Option[Long],
  viewSize: Option[Long]
)

