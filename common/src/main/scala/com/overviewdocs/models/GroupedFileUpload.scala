package com.overviewdocs.models

import java.util.UUID

/** An in-progress upload.
  *
  * Each GroupedFileUpload has its own exclusive Large Object ID. The
  * <tt>guid</tt> is provided by the client (so it can't be trusted). The
  * <tt>size</tt> is provided in client headers, and the <tt>uploadedSize</tt>
  * is derived. When <tt>size == uploadedSize</tt>, the whole file has been
  * uploaded. (As far as we're concerned, that is. If the user appends more
  * data, that's a client error; we'll just ignore the extra bytes.)
  */
case class GroupedFileUpload(
  id: Long,
  fileGroupId: Long,
  guid: UUID,
  contentType: String,
  name: String,
  size: Long,
  uploadedSize: Long,
  contentsOid: Long
)

object GroupedFileUpload {
  case class CreateAttributes(
    fileGroupId: Long,
    guid: UUID,
    contentType: String,
    name: String,
    size: Long
  )
}
