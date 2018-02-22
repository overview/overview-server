package com.overviewdocs.ingest.convert

sealed trait MinimportWorkerType {
  val key: String
}

object MinimportWorkerType {
  case object Office2Pdf extends MinimportWorkerType {
    override val key = "office2pdf"
  }

  case object PdfOcr extends MinimportWorkerType {
    override val key = "pdfocr"
  }

  case object Pdf2Pdf extends MinimportWorkerType {
    override val key = "pdf2pdf"
  }
}
