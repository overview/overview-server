package models.archive

import java.io.InputStream

case class ArchiveEntry(size: Long, name: String, data: () => InputStream)
