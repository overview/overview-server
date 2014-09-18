package models.archive

case class ArchiveEntry(size: Long, name: String, data: CRCInputStream)
