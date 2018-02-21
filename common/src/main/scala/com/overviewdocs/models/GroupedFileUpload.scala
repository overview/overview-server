package com.overviewdocs.models

import java.util.UUID
import play.api.libs.json.JsObject

/** An in-progress upload.
  *
  * Each GroupedFileUpload has its own exclusive Large Object ID. The
  * <tt>guid</tt> is provided by the client (so it can't be trusted). The
  * <tt>size</tt> is provided in client headers, and the <tt>uploadedSize</tt>
  * is derived. When <tt>size == uploadedSize</tt>, the whole file has been
  * uploaded. (As far as we're concerned, that is. If the user appends more
  * data, that's a client error; we'll just ignore the extra bytes.)
  *
  * == TODO: nix LargeObject, use file2Id ==
  *
  * Users should be uploading files directly to BlobStorage, because it's
  * cheaper and faster. Until that refactor, we use a (temporary) worker
  * component to transfer the Large Object to a file2.
  *
  * During resume, the worker component needs to resume these transfers and
  * know which ones completed. So the worker will write file2Id.
  *
  * In the future, end-users will upload to a File2 directly. contentType,
  * name, documentMetadataJson, uploadedSize and contentsOid will likely
  * end up in different tables.
  */
case class GroupedFileUpload(
  id: Long,
  fileGroupId: Long,
  guid: UUID,
  contentType: String,
  name: String,
  documentMetadataJson: Option[JsObject],
  size: Long,
  uploadedSize: Long,
  contentsOid: Long,
  file2Id: Option[Long]
)

object GroupedFileUpload {
  case class CreateAttributes(
    fileGroupId: Long,
    guid: UUID,
    contentType: String,
    name: String,
    documentMetadataJson: Option[JsObject],
    size: Long
  )
}
