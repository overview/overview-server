package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream

trait DocumentTypeDetector {
  def detect(filename: String, stream: InputStream): DocumentTypeDetector.DocumentType
}

object DocumentTypeDetector {

  trait DocumentType
  case object PdfDocument extends DocumentType
}
