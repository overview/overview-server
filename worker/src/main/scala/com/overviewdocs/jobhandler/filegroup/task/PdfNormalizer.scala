package com.overviewdocs.jobhandler.filegroup.task

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.util.{Configuration,Logger,JavaCommand}

/** Makes a PDF into a searchable PDF using pdfocr.
  *
  * The actual conversion happens in a separate process, because some user input
  * is very likely to cause OutOfMemoryError otherwise.
  */
trait PdfNormalizer {
  protected val logger: Logger

  private val ProgressRegex = """^(\d+)/(\d+)$""".r
  private val Newline = '\n'.toByte

  /** Runs the actual conversion.
    *
    * Returns a Future[Right[Unit]], but gracefully handles error by returning
    * Future[Left[String]].
    *
    * The caller should delete `out` when finished with it.
    *
    * On failure, `out` may or may not exist.
    *
    * @param in Input file, which must exist.
    * @param out Output file, which we may write (or may not).
    * @param lang Two-char language code, such as "en".
    * @param onProgress Function we'll call in between steps. If it returns
    *                   `false`, we'll cancel.
    */
  def makeSearchablePdf(
    in: Path,
    out: Path,
    lang: String,
    onProgress: (Int, Int) => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Unit]] = {
    val cmd = command(in, out, lang)
    @volatile var error: Option[String] = None

    logger.info("MakeSearchablePdf {} -> {} ({})", in.toString, out.toString, lang)

    def processOutput(child: Process): Unit = {
      // Don't use a BufferedReader: on Oracle JVM its read() can block after
      // the child exits.
      //
      // Reproduce the error, if you insist upon adding a BufferedReader:
      //
      // 1. Start a Docker VM
      // 2. Run worker in there
      // 3. Feed it a file that needs OCR.

      val stream = child.getInputStream

      def handleLine(line: String): Unit = line match {
        case ProgressRegex(numerator, denominator) => {
          if (!onProgress(numerator.toInt, denominator.toInt)) {
            error = Some("You cancelled an import job")
            child.destroyForcibly // and keep processing....
          }
        }
        case s: String if error.isEmpty && s.length > 0 => error = Some(s)
        case _ => {}
      }

      val buf: Array[Byte] = new Array(1000) // an error message might be a bit long
      val nextLine = mutable.ArrayBuffer[Byte]() // will contain everything up to `\n`

      while (true) {
        val bufSize = stream.read(buf)

        if (bufSize == -1) {
          // We're at the end of the file. If there's any text in
          // nextLinePieces, then the file *didn't* end in `\n`, and that's
          // an error.
          nextLine.++=(buf.take(bufSize))
          val text = new String(nextLine.toArray, StandardCharsets.UTF_8).trim
          if (text.nonEmpty && error.isEmpty) error = Some(text)
          stream.close
          return
        }

        var prevI: Int = 0
        while (prevI < bufSize) {
          val i = buf.indexOf(Newline, prevI)

          if (i > -1 && i < bufSize) {
            nextLine.++=(buf.slice(prevI, i))
            handleLine(new String(nextLine.toArray, StandardCharsets.UTF_8))
            nextLine.clear
            prevI = i + 1
          } else {
            nextLine.++=(buf.slice(prevI, bufSize))
            prevI = bufSize // break out of inner loop
          }
        }
      }
    }

    Future(blocking {
      val process: Process = new ProcessBuilder(cmd: _*)
        .redirectError(ProcessBuilder.Redirect.INHERIT) // Output errors where Logger can see them
        .start
      process.getOutputStream.close
      processOutput(process)
      process.waitFor
    }).map { retval =>
      if (retval != 0 && error.isEmpty) {
        error = Some("PDF is valid, but a bug in Overview means we can't load it")
      }

      error.toLeft(())
    }
  }

  private def command(in: Path, out: Path, lang: String): Seq[String] = JavaCommand(
    "-Xmx" + Configuration.getString("pdf_memory"),
    "com.overviewdocs.helpers.MakeSearchablePdf",
    in.toString,
    out.toString,
    lang
  )
}

object PdfNormalizer extends PdfNormalizer {
  override protected val logger = Logger.forClass(getClass)
}
