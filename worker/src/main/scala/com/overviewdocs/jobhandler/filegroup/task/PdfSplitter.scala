package com.overviewdocs.jobhandler.filegroup.task

import com.google.common.io.ByteStreams
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files,Path,Paths}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.pdfocr.SplitPdfAndExtractTextParser
import com.overviewdocs.util.{Configuration,Logger,JavaCommand}

/** Finds page info from a PDF.
  *
  * The actual conversion happens in a separate process, because some user input
  * is very likely to cause OutOfMemoryError otherwise.
  */
trait PdfSplitter {
  protected val logger: Logger

  import com.overviewdocs.pdfocr.{SplitPdfAndExtractTextReader=>Reader}

  /** Runs the actual conversion.
    *
    * Returns a Future[Right[Seq[PdfSplitter.PageInfo]]] on success and a
    * Future[Left[String]] if there's a failure we can handle gracefully --
    * for instance: "PDF is encrypted" or "this file is not a PDF". Or the
    * catch-all, "Problem parsing PDF"
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
  )(implicit ec: ExecutionContext): Future[Reader.ReadAllResult] = {
    val cmd = command(in, createPdfs)
    logger.info(cmd.mkString(" "))

    def readFromAndWaitForProcess(child: Process, tempDir: Path): Future[Reader.ReadAllResult] = {
      val parser = new SplitPdfAndExtractTextParser(child.getInputStream)
      val reader = new Reader(parser, tempDir)

      var lastOnProgressResponse = true
      def onProgressInner(i1: Int, i2: Int): Boolean = {
        lastOnProgressResponse = onProgress(i1, i2)
        lastOnProgressResponse
      }

      reader.readAll(onProgressInner)
        .flatMap(result => Future(blocking {
          if (lastOnProgressResponse == false) {
            child.destroyForcibly
          }

          // Does child.waitFor block if the input stream buffer never
          // empties? Let's play it safe and empty the buffer.
          try {
            ByteStreams.exhaust(child.getInputStream)
          } catch {
            case _: java.io.IOException => {} // This error would not surprise us: the child is dead
          }

          (result, lastOnProgressResponse, child.waitFor)
        }))
        .flatMap(_ match {
          // Job completed
          case (result, true, 0) => Future.successful(result)

          // Job cancelled; we called child.destroyForcibly, so child.waitFor _may_ return non-zero
          case (result, false, _) => Future.successful(result)

          // Unexpected error (not even "out of memory" -- that should complete successfully)
          case (result, _, n) => {
            for {
              _ <- result.cleanup
            } yield Reader.ReadAllResult.Error("split-pdf-and-extract-text failed with exit code " + n, result.tempDir)
          }
        })
    }

    Future(blocking {
      val process = new ProcessBuilder(cmd: _*)
        .redirectError(ProcessBuilder.Redirect.INHERIT) // so Logger sees it
        .start
      process.getOutputStream.close
      val dir = Files.createTempDirectory(in.getParent, "pdf-splitter-temp")
      (process, dir)
    })
      .flatMap { case (process: Process, dir: Path) => readFromAndWaitForProcess(process, dir) }
  }

  private def command(in: Path, createPdfs: Boolean): Seq[String] = Seq(
    PdfSplitter.SoftLimitPath, "-d", PdfSplitter.pdfMemoryNBytes.toString,
    "/opt/overview/split-pdf-and-extract-text",
    ("--only-extract=" + (if (createPdfs) "false" else "true")),
    in.toString
  )
}

object PdfSplitter extends PdfSplitter {
  private lazy val pdfMemoryNBytes: Long = {
    val GigR = "^(\\d+)[gG]$".r
    val MegR = "^(\\d+)[mM]$".r
    val KiloR = "^(\\d+)[kK]$".r
    val ByteR = "^(\\d+)[bB]?$".r
    Configuration.getString("pdf_memory") match {
      case GigR(gigs) => gigs.toLong * 1024 * 1024 * 1024
      case MegR(megs) => megs.toLong * 1024 * 1024
      case KiloR(kilos) => kilos.toLong * 1024
      case ByteR(bytes) => bytes.toLong
      case _ => throw new RuntimeException("pdf_memory must be specified as a number with 'G' or 'M', like '1g' or '1500m'")
    }
  }

  private lazy val SoftLimitPath: String = {
    if (Files.exists(Paths.get("/usr/bin/softlimit"))) {
      "/usr/bin/softlimit" // Ubuntu with daemontools installed (dev)
    } else {
      "/sbin/chpst"        // Alpine Linux w/ runit installed (prod)
    }
  }

  override protected val logger = Logger.forClass(getClass)
}
