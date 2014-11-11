package models.archive

trait DocumentViewInfo {
  def removePdf(fileName: String): String = {
    val caseInsensitivePdfExtension = "(?i)\\.pdf$"
    val dot = "\\."
    val dotReplacement = "_"

    fileName.
      replaceAll(caseInsensitivePdfExtension, "").
      replaceAll(dot, dotReplacement)
  }

  def asPdf(fileName: String): String = {
    val Pdf = ".pdf"
    fileName + Pdf
  }

}