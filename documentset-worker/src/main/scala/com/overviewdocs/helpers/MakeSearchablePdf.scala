package com.overviewdocs.helpers

import java.nio.file.{Path,Paths}
import java.util.Locale
import org.overviewproject.pdfocr.PdfOcr
import org.overviewproject.pdfocr.exceptions._
import scala.util.{Failure,Success}

object MakeSearchablePdf extends App {
  /** Converts a PDF into a searchable PDF, using pdfocr.
    *
    * This is a separate program because the amount of memory pdfocr uses
    * depends upon user input. (PDFBox is *supposed* to limit its RAM usage, but
    * it actually doesn't: `BufferedImage`s, used when exporting to Tesseract,
    * can be arbitrarily large.)
    *
    * Outputs progress to stdout as newline-separated fractions. For example:
    *
    *   0/10
    *   1/10
    *   2/10
    *   ...
    *   10/10
    *
    * Kill the program to cancel it. `outPath` may or may not be written.
    *
    * If the program fails with OutOfMemoryError, `outPath` may or may not be
    * written.
    *
    * If pdfocr catches an error halfway through, it will be appended to
    * standard output as a String. (Yes, standard *output*: in this context,
    * an invalid PDF isn't an error.) For instance:
    *
    *   0/10 t Page 1\f
    *   1/10 f Paage 2\f
    *   Invalid PDF file
    *
    * @param inPath Where on the filesystem to find the input file.
    * @param outPath Where on the filesystem to place the output file.
    * @param lang Languages to pass to Tesseract.
    */
  def run(inPath: Path, outPath: Path, locales: Seq[Locale]): Unit = {
    def onProgress(curPage: Int, nPages: Int): Boolean = {
      System.out.print(s"$curPage/$nPages\n")
      true
    }

    implicit val ec = CrashyExecutionContext()

    def success(message: String): Unit = {
      System.out.print(message + "\n")
      ec.shutdown
    }

    def failure(ex: Throwable): Unit = {
      ex.printStackTrace()
      Runtime.getRuntime.halt(1)
    }

    PdfOcr.makeSearchablePdf(inPath, outPath, locales, onProgress).onComplete {
      case Success(_) => ec.shutdown
      case Failure(_: PdfInvalidException) => success("Error in PDF file")
      case Failure(_: PdfEncryptedException) => success("PDF file is password-protected")
      case Failure(ex) => failure(ex)
    }

    ec.awaitTermination(999999, java.util.concurrent.TimeUnit.DAYS)
  }

  if (args.length != 3) {
    System.err.println("Example usage: MakeSearchablePdf in.pdf out.pdf en")
  }

  val inPath = Paths.get(args(0))
  val outPath = Paths.get(args(1))
  val locales = Seq(new Locale(args(2)))

  run(inPath, outPath, locales)
}

// sbt command to run this program:
// ./sbt 'documentset-worker/run-main com.overviewdocs.helpers.MakeSearchablePdf in.pdf out.pdf en'
