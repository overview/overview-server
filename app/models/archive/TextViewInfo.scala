package models.archive

import java.io.InputStream

abstract class TextViewInfo(
    suppliedId: Option[String],
    title: Option[String],
    documentId: Long,
    size: Long) extends DocumentViewInfo {

  override def archiveEntry: ArchiveEntry = {
    val filename = suppliedId
      .orElse(title)
      .get

    ArchiveEntry(asTxt(filename), size, textInputStream(documentId) _)
  }

  private def asTxt(filename: String): String = {
    val Txt = ".txt"
    filename + Txt
  }

  protected def textInputStream(documentId: Long)(): InputStream
}