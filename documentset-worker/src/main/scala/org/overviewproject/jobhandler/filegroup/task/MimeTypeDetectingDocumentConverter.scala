package org.overviewproject.jobhandler.filegroup.task

import java.io.{BufferedInputStream,InputStream}
import java.util.UUID
import scala.util.control.NonFatal

trait MimeTypeDetectingDocumentConverter extends DocumentConverter {
  val mimeTypeDetector: MimeTypeDetectingDocumentConverter.MimeTypeDetector
  val mimeTypeToConverter: Map[String,DocumentConverter]

  override def withStreamAsPdf[T](guid: UUID, filename: String, inputStream: InputStream)(f: InputStream => T) = {
    val bufferedInputStream = new BufferedInputStream(inputStream, mimeTypeDetector.nBytesNeededToDetectMimeType)

    val mimeType = try {
      mimeTypeDetector.detectMimeType(filename, bufferedInputStream)
    } catch {
      case NonFatal(e) => throw new MimeTypeDetectingDocumentConverter.CouldNotDetectMimeTypeException(filename, e)
    }

    val converter = mimeTypeToConverter.get(mimeType) match {
      case Some(x) => x
      case None => {
        // "text/plain" failed? Try "text/*"
        val parentType = mimeType.replaceFirst("/.*$", "/*")
        mimeTypeToConverter.get(parentType) match {
          case Some(x) => x
          case None => throw new MimeTypeDetectingDocumentConverter.DocumentConverterDoesNotExistException(filename, mimeType)
        }
      }
    }

    converter.withStreamAsPdf(guid, filename, bufferedInputStream)(f)

    // We don't need to close() bufferedInputStream: it will be
    // garbage-collected and the caller will close inputStream.
  }
}

object MimeTypeDetectingDocumentConverter extends MimeTypeDetectingDocumentConverter {
  class CouldNotDetectMimeTypeException(val filename: String, val throwable: Throwable) extends Exception(s"Failed to read file $filename", throwable)
  class DocumentConverterDoesNotExistException(val filename: String, val mimeType: String) extends Exception(s"Overview does not know how to read $filename, of type $mimeType")

  override val mimeTypeDetector = DefaultMimeTypeDetector
  override val mimeTypeToConverter = Map(
    // LibreOfficeDocumentConverter: got this list by looking at .desktop files
    // for every LibreOffice application on Ubuntu 14.04
    "application/clarisworks"                                                   -> LibreOfficeDocumentConverter,
    "application/excel"                                                         -> LibreOfficeDocumentConverter,
    "application/macwriteii"                                                    -> LibreOfficeDocumentConverter,
    "application/msexcel"                                                       -> LibreOfficeDocumentConverter,
    "application/mspowerpoint"                                                  -> LibreOfficeDocumentConverter,
    "application/msword"                                                        -> LibreOfficeDocumentConverter,
    "application/prs.plucker"                                                   -> LibreOfficeDocumentConverter,
    "application/rtf"                                                           -> LibreOfficeDocumentConverter,
    "application/tab-separated-values"                                          -> LibreOfficeDocumentConverter,
    "application/vnd.corel-draw"                                                -> LibreOfficeDocumentConverter,
    "application/vnd.lotus-1-2-3"                                               -> LibreOfficeDocumentConverter,
    "application/vnd.lotus-wordpro"                                             -> LibreOfficeDocumentConverter,
    "application/vnd.ms-excel"                                                  -> LibreOfficeDocumentConverter,
    "application/vnd.ms-excel.sheet.binary.macroenabled.12"                     -> LibreOfficeDocumentConverter,
    "application/vnd.ms-excel.sheet.macroenabled.12"                            -> LibreOfficeDocumentConverter,
    "application/vnd.ms-excel.template.macroenabled.12"                         -> LibreOfficeDocumentConverter,
    "application/vnd.ms-powerpoint"                                             -> LibreOfficeDocumentConverter,
    "application/vnd.ms-powerpoint.presentation.macroenabled.12"                -> LibreOfficeDocumentConverter,
    "application/vnd.ms-powerpoint.slideshow.macroEnabled.12"                   -> LibreOfficeDocumentConverter,
    "application/vnd.ms-powerpoint.template.macroenabled.12"                    -> LibreOfficeDocumentConverter,
    "application/vnd.ms-publisher"                                              -> LibreOfficeDocumentConverter,
    "application/vnd.ms-word"                                                   -> LibreOfficeDocumentConverter,
    "application/vnd.ms-word.document.macroenabled.12"                          -> LibreOfficeDocumentConverter,
    "application/vnd.ms-word.template.macroenabled.12"                          -> LibreOfficeDocumentConverter,
    "application/vnd.ms-works"                                                  -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.chart"                                  -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.chart-template"                         -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.graphics"                               -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.graphics-flat-xml"                      -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.graphics-template"                      -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.presentation"                           -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.presentation-flat-xml"                  -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.presentation-template"                  -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.spreadsheet"                            -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.spreadsheet-flat-xml"                   -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.spreadsheet-template"                   -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.text"                                   -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.text-flat-xml"                          -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.text-master"                            -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.text-template"                          -> LibreOfficeDocumentConverter,
    "application/vnd.oasis.opendocument.text-web"                               -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.presentationml.slide"        -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow"    -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.presentationml.template"     -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.template"      -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"   -> LibreOfficeDocumentConverter,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.template"   -> LibreOfficeDocumentConverter,
    "application/vnd.palm"                                                      -> LibreOfficeDocumentConverter,
    "application/vnd.stardivision.writer-global"                                -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.calc"                                              -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.calc.template"                                     -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.draw"                                              -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.draw.template"                                     -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.impress"                                           -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.impress.template"                                  -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.writer"                                            -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.writer.global"                                     -> LibreOfficeDocumentConverter,
    "application/vnd.sun.xml.writer.template"                                   -> LibreOfficeDocumentConverter,
    "application/vnd.visio"                                                     -> LibreOfficeDocumentConverter,
    "application/vnd.wordperfect"                                               -> LibreOfficeDocumentConverter,
    "application/wordperfect"                                                   -> LibreOfficeDocumentConverter,
    "application/x-123"                                                         -> LibreOfficeDocumentConverter,
    "application/x-aportisdoc"                                                  -> LibreOfficeDocumentConverter,
    "application/x-dbase"                                                       -> LibreOfficeDocumentConverter,
    "application/x-dbf"                                                         -> LibreOfficeDocumentConverter,
    "application/x-doc"                                                         -> LibreOfficeDocumentConverter,
    "application/x-dos_ms_excel"                                                -> LibreOfficeDocumentConverter,
    "application/x-excel"                                                       -> LibreOfficeDocumentConverter,
    "application/x-extension-txt"                                               -> LibreOfficeDocumentConverter,
    "application/x-fictionbook+xml"                                             -> LibreOfficeDocumentConverter,
    "application/x-hwp"                                                         -> LibreOfficeDocumentConverter,
    "application/x-iwork-keynote-sffkey"                                        -> LibreOfficeDocumentConverter,
    "application/x-msexcel"                                                     -> LibreOfficeDocumentConverter,
    "application/x-ms-excel"                                                    -> LibreOfficeDocumentConverter,
    "application/x-quattropro"                                                  -> LibreOfficeDocumentConverter,
    "application/x-t602"                                                        -> LibreOfficeDocumentConverter,
    "application/x-wpg"                                                         -> LibreOfficeDocumentConverter,
    "image/x-freehand"                                                          -> LibreOfficeDocumentConverter,

    // Text types: we're using LibreOffice now, but we probably shouldn't
    // https://www.pivotaltracker.com/story/show/76453196
    // https://www.pivotaltracker.com/story/show/76453264
    "application/csv"             -> LibreOfficeDocumentConverter,
    "application/javascript"      -> LibreOfficeDocumentConverter,
    "application/json"            -> LibreOfficeDocumentConverter,
    "application/xml"             -> LibreOfficeDocumentConverter,
    "text/comma-separated-values" -> LibreOfficeDocumentConverter,
    "text/html"                   -> LibreOfficeDocumentConverter,
    "text/*"                      -> LibreOfficeDocumentConverter
  )

  trait MimeTypeDetector {
    def detectMimeType(filename: String, inputStream: BufferedInputStream): String
    def nBytesNeededToDetectMimeType: Int
  }

  object DefaultMimeTypeDetector extends MimeTypeDetector {
    import org.overviewproject.mime_types.{MimeTypeDetector => JMimeTypeDetector}
    private lazy val detector = new JMimeTypeDetector()

    override def detectMimeType(filename: String, inputStream: BufferedInputStream) = {
      detector.detectMimeType(filename, inputStream)
    }

    override def nBytesNeededToDetectMimeType = detector.getMaxGetBytesLength
  }
}
