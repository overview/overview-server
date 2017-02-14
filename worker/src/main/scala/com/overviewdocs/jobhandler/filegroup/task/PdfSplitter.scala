package com.overviewdocs.jobhandler.filegroup.task

import java.io.IOException
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
    val pageFilenamePattern = in.getFileName + "-p{}"
    val maybeOutPattern = if (createPdfs) {
      Some(pageFilenamePattern)
    } else {
      None
    }

    val cmd = command(in, pageFilenamePattern, createPdfs)
    logger.info(cmd.mkString(" "))

    val pageInfos = mutable.ArrayBuffer[PdfSplitter.PageInfo]()
    @volatile var error: Option[String] = None

    def processOutput(child: Process): Unit = {
      def dataToPageInfo(bytes: Array[Byte]): Unit = {
        val str = new String(bytes, StandardCharsets.UTF_8)
        str match {
          case PageInfoRegex(pageNumber, nPages, isFromOcr, text) => {
            // create preview for page ?
            pageInfos.+=(PdfSplitter.PageInfo(
              pageNumber.toInt,
              nPages.toInt,
              isFromOcr == "t",
              text,
              maybeOutPattern.map(p => in.resolveSibling(p.replace("{}", pageNumber))),
              if (createPdfs || pageNumber == "1")
                Some(in.resolveSibling(pageFilenamePattern.replace("{}", pageNumber + "-thumbnail") + ".png"))
              else
                None
            ))
            if (!onProgress(pageNumber.toInt, nPages.toInt)) {
              error = Some("You cancelled an import job")
              child.destroyForcibly
            }
          }
          case _ => {
            throw new IllegalArgumentException(s"Invalid data from splitter: $str")
          }
        }
      }

      val stream = child.getInputStream
      val buf: Array[Byte] = new Array(10 * 1024) // 10kb covers most pages' text.
      var nextPageBytes = mutable.ArrayBuffer[Byte]() // When complete, all but the final `\f` will be in here.

      while (true) {
        val bufSize = stream.read(buf)
        if (bufSize == -1) {
          // We're at the end of the file. If there's any text in
          // nextPageBytes, that means we didn't find `\f`. So the text is an
          // error message.
          nextPageBytes.++=(buf.take(bufSize))
          val text = new String(nextPageBytes.toArray, StandardCharsets.UTF_8).trim
          if (text.nonEmpty && error.isEmpty) error = Some(text)
          stream.close
          return
        }

        var prevI: Int = 0
        while (prevI < bufSize) {
          val i = buf.indexOf(FormFeed, prevI)

          if (i > -1 && i < bufSize) {
            nextPageBytes.++=(buf.slice(prevI, i))
            dataToPageInfo(nextPageBytes.toArray)
            nextPageBytes.clear
            prevI = i + 1 // after \f comes \n, but we can't skip it here: it might be in the next buf
          } else {
            nextPageBytes.++=(buf.slice(prevI, bufSize))
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

      try {
        processOutput(process)
      } catch {
        case _: IOException => {
          // The stream was closed by something else (i.e., the child). We don't
          // much care what happens, because we assume the child's exit code will
          // be non-zero.
        }
      }

      process.waitFor
    }).flatMap { retval =>
      if (retval != 0 && error.isEmpty) {
        error = Some("PDF is valid, but a bug in Overview means we can't load it")
      }

      val ret = error.toLeft(pageInfos.toSeq)

      ret match {
        case Right(_) => Future.successful(ret)
        case Left(_) => {
          val nextPdfPath: Option[Path] = maybeOutPattern.map(p => in.resolveSibling(p.replace("{}", (pageInfos.length + 1).toString)))
          val nextThumbnailPath: Option[Path] = maybeOutPattern.map(p => in.resolveSibling(p.replace("{}", (pageInfos.length + 1) + "-thumbnail") + ".png"))

          val paths = pageInfos.flatMap(_.pdfPath) ++ nextPdfPath ++ pageInfos.flatMap(_.thumbnailPath) ++ nextThumbnailPath

          println(paths)
          Future(blocking {
            paths.foreach(_.toFile.delete())
            ret
          })
        }
      }
    }
  }

  private def command(in: Path, pageFilenamePattern: String, createPdfs: Boolean): Seq[String] = JavaCommand(
    "-Xmx" + Configuration.getString("pdf_memory"),
    "com.overviewdocs.helpers.SplitPdfAndExtractText",
    in.toString,
    pageFilenamePattern
  ) ++ (if(createPdfs) Seq("--create-pdfs") else Nil)
}

object PdfSplitter extends PdfSplitter {
  /** Details about a page we've extracted.
    */
  case class PageInfo(
    /** Page number, starting at 1. */
    pageNumber: Int,

    /** Total number of pages. */
    nPages: Int,

    /** If true, pdfocr may have generated some of this text via Tesseract. */
    isFromOcr: Boolean,

    /** The text contents of the page.
      *
      * These have already have Textify() called on them, by
      * SplitPdfAndExtractText.
      */
    text: String,

    /** If set, a file on the filesystem containing a PDF version of this page.
      */
    pdfPath: Option[Path],

    /** If set, a file on the filesystem containing a PNG thumbnail version of this page.
      */
    thumbnailPath: Option[Path]

  )

  override protected val logger = Logger.forClass(getClass)
}
