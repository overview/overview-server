package models.archive

import java.io.InputStream

case class ArchiveEntry(name: String, size: Long, data: () => InputStream)
