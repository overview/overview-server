package com.overviewdocs.jobhandler.filegroup.task

import java.io.{BufferedReader,IOException,InputStreamReader,StringReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Path,Paths}
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

    // This gets its own thread
    def handleOutputFromChild(child: Process): Unit = {
      val stream = child.getInputStream

      val reader = new BufferedReader(try {
        new InputStreamReader(stream, StandardCharsets.UTF_8)
      } catch {
        case _: IOException => {
          // The child is dead already!
          new StringReader("The process didn't start") // an error message we'll read
        }
      })

      def nextLine: Option[String] = {
        try {
          Option(reader.readLine)
        } catch {
          case _: IOException => {
            // The stream was closed by something else (i.e., the child). We
            // don't much care what happens: either the child already output an
            // error and we read it, or the exit code is non-zero and we *will*
            // write an error.
            None
          }
        }
      }

      def step: Boolean = {
        nextLine match {
          case None => false
          case Some(line) => line match {
            case ProgressRegex(numerator, denominator) => {
              if (!onProgress(numerator.toInt, denominator.toInt)) {
                error = Some("You cancelled an import job")
                child.destroyForcibly
              }
              true
            }
            case s: String if s.trim.length > 0 && error.isEmpty => {
              error = Some(s.trim)
              true // Eat from the child, so it can exit
            }
            case _ => true
          }
        }
      }
      while (error.isEmpty && step) {}

      try {
        stream.close
      } catch {
        case _: IOException => {}
      }
    }

    Future(blocking {
      val process: Process = new ProcessBuilder(cmd: _*)
        .redirectError(ProcessBuilder.Redirect.INHERIT) // Output errors where Logger can see them
        .start

      process.getOutputStream.close
      handleOutputFromChild(process)
      process.waitFor
    })
      .map { retval =>
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
