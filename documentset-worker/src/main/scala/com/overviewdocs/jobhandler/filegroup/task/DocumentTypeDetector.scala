package com.overviewdocs.jobhandler.filegroup.task

import java.io.InputStream
import org.overviewproject.mime_types.MimeTypeDetector

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.postgres.LargeObjectInputStream

trait DocumentTypeDetector {
  protected val mimeTypeDetector: MimeTypeDetector

  private val mimeTypeToDocumentType = {
    import DocumentTypeDetector._

    Map(
      "application/pdf" -> PdfDocument,
      
      "application/clarisworks" -> OfficeDocument,
      "application/excel" -> OfficeDocument,
      "application/macwriteii" -> OfficeDocument,
      "application/msexcel" -> OfficeDocument,
      "application/mspowerpoint" -> OfficeDocument,
      "application/msword" -> OfficeDocument,
      "application/prs.plucker" -> OfficeDocument,
      "application/rtf" -> OfficeDocument,
      "application/tab-separated-values" -> OfficeDocument,
      "application/vnd.corel-draw" -> OfficeDocument,
      "application/vnd.lotus-1-2-3" -> OfficeDocument,
      "application/vnd.lotus-wordpro" -> OfficeDocument,
      "application/vnd.ms-excel" -> OfficeDocument,
      "application/vnd.ms-excel.sheet.binary.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-excel.sheet.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-excel.template.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-powerpoint" -> OfficeDocument,
      "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-powerpoint.slideshow.macroEnabled.12" -> OfficeDocument,
      "application/vnd.ms-powerpoint.template.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-publisher" -> OfficeDocument,
      "application/vnd.ms-word" -> OfficeDocument,
      "application/vnd.ms-word.document.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-word.template.macroenabled.12" -> OfficeDocument,
      "application/vnd.ms-works" -> OfficeDocument,
      "application/vnd.oasis.opendocument.chart" -> OfficeDocument,
      "application/vnd.oasis.opendocument.chart-template" -> OfficeDocument,
      "application/vnd.oasis.opendocument.graphics" -> OfficeDocument,
      "application/vnd.oasis.opendocument.graphics-flat-xml" -> OfficeDocument,
      "application/vnd.oasis.opendocument.graphics-template" -> OfficeDocument,
      "application/vnd.oasis.opendocument.presentation" -> OfficeDocument,
      "application/vnd.oasis.opendocument.presentation-flat-xml" -> OfficeDocument,
      "application/vnd.oasis.opendocument.presentation-template" -> OfficeDocument,
      "application/vnd.oasis.opendocument.spreadsheet" -> OfficeDocument,
      "application/vnd.oasis.opendocument.spreadsheet-flat-xml" -> OfficeDocument,
      "application/vnd.oasis.opendocument.spreadsheet-template" -> OfficeDocument,
      "application/vnd.oasis.opendocument.text" -> OfficeDocument,
      "application/vnd.oasis.opendocument.text-flat-xml" -> OfficeDocument,
      "application/vnd.oasis.opendocument.text-master" -> OfficeDocument,
      "application/vnd.oasis.opendocument.text-template" -> OfficeDocument,
      "application/vnd.oasis.opendocument.text-web" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.presentationml.slide" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.presentationml.slideshow" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.presentationml.template" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.spreadsheetml.template" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> OfficeDocument,
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template" -> OfficeDocument,
      "application/vnd.palm" -> OfficeDocument,
      "application/vnd.stardivision.writer-global" -> OfficeDocument,
      "application/vnd.sun.xml.calc" -> OfficeDocument,
      "application/vnd.sun.xml.calc.template" -> OfficeDocument,
      "application/vnd.sun.xml.draw" -> OfficeDocument,
      "application/vnd.sun.xml.draw.template" -> OfficeDocument,
      "application/vnd.sun.xml.impress" -> OfficeDocument,
      "application/vnd.sun.xml.impress.template" -> OfficeDocument,
      "application/vnd.sun.xml.writer" -> OfficeDocument,
      "application/vnd.sun.xml.writer.global" -> OfficeDocument,
      "application/vnd.sun.xml.writer.template" -> OfficeDocument,
      "application/vnd.visio" -> OfficeDocument,
      "application/vnd.wordperfect" -> OfficeDocument,
      "application/wordperfect" -> OfficeDocument,
      "application/x-123" -> OfficeDocument,
      "application/x-aportisdoc" -> OfficeDocument,
      "application/x-dbase" -> OfficeDocument,
      "application/x-dbf" -> OfficeDocument,
      "application/x-doc" -> OfficeDocument,
      "application/x-dos_ms_excel" -> OfficeDocument,
      "application/x-excel" -> OfficeDocument,
      "application/x-extension-txt" -> OfficeDocument,
      "application/x-fictionbook+xml" -> OfficeDocument,
      "application/x-hwp" -> OfficeDocument,
      "application/x-iwork-keynote-sffkey" -> OfficeDocument,
      "application/x-msexcel" -> OfficeDocument,
      "application/x-ms-excel" -> OfficeDocument,
      "application/x-quattropro" -> OfficeDocument,
      "application/x-t602" -> OfficeDocument,
      "application/x-wpg" -> OfficeDocument,
      "image/x-freehand" -> OfficeDocument,

      // Text types: we're using LibreOffice now, but we probably shouldn't
      // https://www.pivotaltracker.com/story/show/76453196
      // https://www.pivotaltracker.com/story/show/76453264
      "application/csv" -> OfficeDocument,
      "application/javascript" -> OfficeDocument,
      "application/json" -> OfficeDocument,
      "application/xml" -> OfficeDocument,
      "text/comma-separated-values" -> OfficeDocument,
      "text/html" -> OfficeDocument,
      "text/*" -> OfficeDocument
    )
  }

  def detect(filename: String, stream: InputStream): DocumentTypeDetector.DocumentType = {
    val mimeType = mimeTypeDetector.detectMimeType(filename, stream)

    mimeTypeToDocumentType.get(mimeType)
      .orElse(mimeTypeToDocumentType.get(parentType(mimeType)))
      .getOrElse(DocumentTypeDetector.UnsupportedDocument(mimeType))

  }

  private def parentType(mimeType: String): String = mimeType.replaceFirst("/.*$", "/*")
  private def maximumBytesRead: Int = mimeTypeDetector.getMaxGetBytesLength
}

object DocumentTypeDetector extends DocumentTypeDetector with HasBlockingDatabase {
  sealed trait DocumentType
  case object PdfDocument extends DocumentType
  case object OfficeDocument extends DocumentType
  //case object TextDocument extends DocumentType
  //case object HtmlDocument extends DocumentType
  case class UnsupportedDocument(mimeType: String) extends DocumentType

  override protected val mimeTypeDetector = new MimeTypeDetector

  def detectForLargeObject(filename: String, oid: Long): DocumentTypeDetector.DocumentType = {
    val stream = new LargeObjectInputStream(oid, blockingDatabase)
    detect(filename, stream)
  }
}
