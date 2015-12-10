package com.overviewdocs.jobhandler.filegroup.task

import java.io.{BufferedReader,InputStream,InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Path,Paths}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.util.{Configuration,Logger,JavaCommand}

/** Finds page info from a PDF.
  *
  * The actual conversion happens in a separate process, because some user input
  * is very likely to cause OutOfMemoryError otherwise.
  */
trait PdfSplitter {
  protected val logger: Logger

  private val PageInfoRegex = """(?s)\n?(\d+)/(\d+) ([tf]) (.*)""".r
  private val FormFeed = 0x0c.toByte

  /** Runs the actual conversion.
    *
    * Returns a Future[Right[Seq[PdfSplitter.PageInfo]]] on success and a
    * Future[Left[String]] if there's a failure we can handle gracefully --
    * for instance: "PDF is encrypted" or "this file is not a PDF". Or the
    * catch-all, "PDF is valid, but a bug in Overview means we can't load it."
    *
    * If called with `createPdfs`, each `PageInfo` will include a `Path` to a
    * newly-created file on the filesystem. Delete each file when you're done
    * with it. Also, ensure the basename (i.e., filename without suffix) of
    * `in` is unique across all calls ever, because we'll be outputting all
    * pages to the same directory.
    *
    * On failure/cancel, we make a best effort to delete all previously-created
    * files.
    *
    * @param in Input PDF file.
    * @param createPdfs if `true`, extract and write one PDF file per page.
    * @param onProgress progress callback; if it returns `false`, we cancel.
    */
  def splitPdf(
    in: Path,
    createPdfs: Boolean,
    onProgress: (Int, Int) => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[PdfSplitter.PageInfo]]] = {
    val maybeOutPattern: Option[Path] = if (createPdfs) {
      Some(TempDirectory.path.resolve(in.getFileName.toString + "-p{}"))
    } else {
      None
    }

    val cmd = command(in, maybeOutPattern)
    logger.info("SplitPdfAndExtractText {} {}", in, maybeOutPattern)

    val pageInfos = mutable.ArrayBuffer[PdfSplitter.PageInfo]()
    @volatile var error: Option[String] = None

    def dataToPageInfo(bytes: Array[Byte], abort: => Unit): Unit = {
      val str = new String(bytes, StandardCharsets.UTF_8)
      str match {
        case PageInfoRegex(pageNumber, nPages, isFromOcr, text) => {
          pageInfos.+=(PdfSplitter.PageInfo(
            pageNumber.toInt,
            nPages.toInt,
            isFromOcr == "t",
            text,
            maybeOutPattern.map(p => p.resolveSibling(p.getFileName.toString.replace("{}", pageNumber)))
          ))
          if (!onProgress(pageNumber.toInt, nPages.toInt)) {
            error = Some("You cancelled an import job")
            abort
          }
        }
        case _ => {
          throw new IllegalArgumentException(s"Invalid data from splitter: $str")
        }
      }
    }

    def processOutput(stream: InputStream, abort: => Unit): Unit = {
      val buf: Array[Byte] = new Array(10 * 1024) // 10kb covers most pages' text.
      var nextPageBuffer = mutable.ArrayBuffer[Byte]() // When complete, all but the final `\f` will be in here.
      while (true) {
        val bufSize = stream.read(buf)
        if (bufSize == -1) {
          // We're at the end of the file. If there's any text in
          // nextPageBuffer, that means we didn't find `\f`. So the text is an
          // error message.
          nextPageBuffer.++=(buf.take(bufSize))
          val text = new String(nextPageBuffer.toArray, StandardCharsets.UTF_8).trim
          if (text.nonEmpty && error.isEmpty) error = Some(text)
          stream.close
          return
        }

        var prevI: Int = 0
        while (prevI < bufSize) {
          val i = buf.indexOf(FormFeed, prevI)

          if (i > -1 && i < bufSize) {
            nextPageBuffer.++=(buf.slice(prevI, i))
            dataToPageInfo(nextPageBuffer.toArray, abort)
            nextPageBuffer.clear
            prevI = i + 1 // after \f comes \n, but we can't skip it here: it might be in the next buf
          } else {
            nextPageBuffer.++=(buf.slice(prevI, bufSize))
            prevI = bufSize // break out of the inner while loop
          }
        }
      }
    }

    Future(blocking {
      val process = new ProcessBuilder(cmd: _*)
        .redirectError(ProcessBuilder.Redirect.INHERIT) // so Logger sees it
        .start
      process.getOutputStream.close
      processOutput(process.getInputStream, process.destroyForcibly _)
      process.waitFor
    }).flatMap { retval =>
      if (retval != 0 && error.isEmpty) {
        error = Some("PDF is valid, but a bug in Overview means we can't load it")
      }

      val ret = error.toLeft(pageInfos.toSeq)

      ret match {
        case Right(_) => Future.successful(ret)
        case Left(_) => {
          val nextPath: Option[Path] = maybeOutPattern.map(p => p.resolveSibling(p.getFileName.toString.replace("{}", (pageInfos.length + 1).toString)))

          val pdfPaths = pageInfos.flatMap(_.pdfPath) ++ nextPath

          Future(blocking {
            pdfPaths.foreach(_.toFile.delete)
            ret
          })
        }
      }
    }
  }

  private def command(in: Path, maybeOutPattern: Option[Path]): Seq[String] = JavaCommand(
    "-Xmx" + Configuration.getString("pdf_memory"),
    "com.overviewdocs.helpers.SplitPdfAndExtractText",
    in.toString
  ) ++ maybeOutPattern.map(_.toString)
}

object PdfSplitter extends PdfSplitter {
  /** Details about a page we've extracted.
    */
  case class PageInfo(
    /** Page number, starting at 1. */
    val pageNumber: Int,

    /** Total number of pages. */
    val nPages: Int,

    /** If true, pdfocr may have generated some of this text via Tesseract. */
    val isFromOcr: Boolean,

    /** The text contents of the page.
      *
      * These have already have Textify() called on them, by
      * SplitPdfAndExtractText.
      */
    val text: String,

    /** If set, a file on the filesystem containing a PDF version of this page.
      */
    val pdfPath: Option[Path]
  )

  override protected val logger = Logger.forClass(getClass)
}
