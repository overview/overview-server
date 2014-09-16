package models.archive

trait ArchiveEntry {
  val size: Long
  val name: String
  val data: CRCInputStream
}