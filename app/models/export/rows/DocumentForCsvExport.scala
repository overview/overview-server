package models.export.rows

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
  tagIds: Seq[Long]
)
