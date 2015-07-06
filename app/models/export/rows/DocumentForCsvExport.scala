package models.export.rows

import play.api.libs.json.JsObject

/** A Document as it appears in a CSV export.
  *
  * This is its own class, apart from Document, because we write raw SQL to
  * generate it.
  */
case class DocumentForCsvExport(
  suppliedId: String,
  title: String,
  text: String,
  url: String,
  metadataJson: JsObject, // JsObject seems to make unit tests more concise than Metadata.
  tagIds: Seq[Long]
)
