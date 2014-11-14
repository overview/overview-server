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
      .getOrElse(documentId.toString)

    ArchiveEntry(asTxt(filename), size, textInputStream(documentId) _)
  }

  private def asTxt(filename: String): String = {
    val Txt = ".txt"
    filename + Txt
  }

  private def textInputStream(documentId: Long)(): InputStream = storage.textInputStream(documentId)
  
  protected val storage: Storage
  protected trait Storage {
    def textInputStream(documentId: Long): InputStream
  }
}