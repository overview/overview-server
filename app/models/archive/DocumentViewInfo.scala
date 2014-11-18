package models.archive

trait DocumentViewInfo {
  
  def archiveEntry: ArchiveEntry

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