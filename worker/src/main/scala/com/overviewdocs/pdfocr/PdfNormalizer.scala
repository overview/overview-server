package com.overviewdocs.pdfocr

import java.nio.file.Path

import com.overviewdocs.util.{Configuration,JavaCommand}

object PdfNormalizer {
  def command(in: Path, out: Path, lang: String): Seq[String] = JavaCommand(
    "-Xmx" + Configuration.getString("pdf_memory"),
    "com.overviewdocs.helpers.MakeSearchablePdf",
    in.toString,
    out.toString,
    lang
  )
}
