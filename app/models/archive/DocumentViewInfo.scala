package models.archive

import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

trait DocumentViewInfo {
  /** Size of the file contents. */
  val size: Long

  /** Filename in the archive. Overridden by implementations. */
  def name: String

  /** Contents. Overridden by implementations. */
  def stream(): Future[Enumerator[Array[Byte]]]
  
  def archiveEntry: ArchiveEntry = ArchiveEntry(name, size, stream _)

  protected def removePdf(fileName: String): String = {
    val caseInsensitivePdfExtension = "(?i)\\.pdf$"
    val dot = "\\."
    val dotReplacement = "_"

    fileName.
      replaceAll(caseInsensitivePdfExtension, "").
      replaceAll(dot, dotReplacement)
  }

  protected def asPdf(fileName: String): String = {
    val Pdf = ".pdf"
    fileName + Pdf
  }
  
  protected def addPageNumber(fileName: String, pageNumber: Int): String = 
    s"$fileName p$pageNumber"

}
