package com.overviewdocs.helpers

import java.nio.file.{Files,Path,Paths,StandardOpenOption}
import org.overviewproject.pdfocr.pdf.PdfDocument
import org.overviewproject.pdfocr.exceptions._
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

import com.overviewdocs.util.Textify

object SplitPdfAndExtractText extends App {
  /** Converts a PDF into a number of PDFs, one per page, using pdfocr.
    *
    * Outputs page contents, as Textified text.
    *
    * This is a separate program because the amount of memory pdfocr uses
    * depends upon user input. (PDFBox is *supposed* to limit its RAM usage, but
    * it actually doesn't: `BufferedImages`, used when exporting to Tesseract,
    * can be arbitrarily large.
    *
    * Outputs progress to stdout as newline-separated fractions and content. For
    * example:
    *
    *   1/10 t The text of page 1.\f
    *   2/10 f The text of
    *   page 2.\f
    *   ...
    *   10/10 Some more text\f
    *
    * That is: for each page, outputs an integer, a `/`, another integer, a
    * ` `, a `t` or `f` (`t` means hasOcr==true), a ` `, and then the UTF-8 text
    * of the page, ending in `\f\n`. (The page text has been run through
    * Textify, so it's guaranteed not to contain `\f`. The `\n` makes things
    * easier to read on a console.)
    *
    * We can only determine whether OCR has been run on a page if the OCR text
    * was added by `pdfocr`. (It injects a special font into the page.)
    *
    * Kill the program to cancel it. `outPathPattern` files may have been
    * written up to one past the last-reported number. For instance, if you
    * cancel after the program outputs 1/10, be sure to check for page 2.
    *
    * If the program fails with OutOfMemoryError, `outPathPattern` files may
    * have been written up to one past the last-reported number.
    *
    * If pdfocr catches an error halfway through, it will be appended to
    * standard output as a String. (Yes, standard *output*: in this context,
    * an invalid PDF isn't an error.) For instance:
    *
    *   0/10 ...
    *   1/10 ...
    *   Invalid PDF file
    *
    * @param inPath Where on the filesystem to find the input file.
    * @param outPathPattern Where on the filesystem to place a PDF file per
    *                       split page. Mark the page number with `{}`. For
    *                       instance: `/tmp/p{}.pdf`. Leave blank and no PDF
    *                       files will be written.
    */
  def run(inPath: Path, maybeOutPathPattern: Option[Path]): Unit = {
    implicit val ec = CrashyExecutionContext()

    val pdfDocument: PdfDocument = try {
      await(PdfDocument.load(inPath))
    } catch {
      case _: PdfInvalidException => {
        System.out.print("Error in PDF file\n")
        return
      }
      case _: PdfEncryptedException => {
        System.out.println("PDF file is password-protected\n")
        return
      }
    }

    val nPages = pdfDocument.nPages
    var currentPageNumber: Int = 0
    val it = pdfDocument.pages

    try {
      // thumbnail for first page
      // png

      // image writer to write thumbnail / check disk
      // start with convention / onto s3

      while (it.hasNext) {
        currentPageNumber += 1
        val pdfPage = await(it.next)
        val isFromOcr: Boolean = pdfPage.isFromOcr
        val text: String = Textify(pdfPage.toText)

        maybeOutPathPattern.foreach { pattern =>
          val pageBytes: Array[Byte] = pdfPage.toPdf
          val outPath = pattern.resolveSibling(pattern.getFileName.toString.replace("{}", currentPageNumber.toString))
          Files.write(outPath, pageBytes, StandardOpenOption.CREATE)

          // output + '-thumbnail'
          // thumbnail for every split page
        }

        System.out.print(s"$currentPageNumber/$nPages ${if (isFromOcr) 't' else 'f'} $text\f\n")
      }
    } catch {
      case _: PdfInvalidException => {
        System.out.print("Error in PDF file\n")
        pdfDocument.close
        return
      }
      case _: PdfEncryptedException => {
        System.out.print("PDF file is password-protected\n")
        pdfDocument.close
        return
      }
    }

    pdfDocument.close
  }

  private def await[A](future: Future[A]): A = Await.result(future, Duration.Inf)

  if (args.length != 1 && args.length != 2) {
    System.err.println("Example usage: SplitPdf in.pdf [out-p{}.pdf]")
  }

  val inPath = Paths.get(args(0))
  val outPath = args.lastOption.map(s => Paths.get(s))

  run(inPath, outPath)
}

// sbt command to run this program:
// ./sbt 'worker/run-main com.overviewdocs.helpers.SplitPdfAndExtractText in.pdf out{}.pdf'
