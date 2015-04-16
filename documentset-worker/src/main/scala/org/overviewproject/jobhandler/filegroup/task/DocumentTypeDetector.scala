package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream
import org.overviewproject.mime_types.MimeTypeDetector


trait DocumentTypeDetector {
  protected val mimeTypeDetector: MimeTypeDetector
  protected val mimeTypeToDocumentType: Map[String, DocumentTypeDetector.DocumentType]

  def detect(filename: String, stream: InputStream): DocumentTypeDetector.DocumentType = {
    val mimeType = mimeTypeDetector.detectMimeType(filename, stream)

     mimeTypeToDocumentType.get(mimeType)
       .orElse(mimeTypeToDocumentType.get(parentType(mimeType)))
       .getOrElse(DocumentTypeDetector.UnsupportedDocument)

  }

  private def parentType(mimeType: String): String = mimeType.replaceFirst("/.*$", "/*")
}

object DocumentTypeDetector {

  trait DocumentType
  case object PdfDocument extends DocumentType
  case object OfficeDocument extends DocumentType
  case object TextDocument extends DocumentType
  case object HtmlDocument extends DocumentType
  case object UnsupportedDocument extends DocumentType
}
