package com.overviewdocs.jobhandler.filegroup.task

import java.nio.file.{Path,Paths}
import scala.concurrent.{ExecutionContext,Future,blocking}
import scala.sys.process.{Process,ProcessLogger}

import com.overviewdocs.util.{Configuration,Logger}

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

    // There's no good reason we have to introduce a race just to be able to
    // kill the process while logging. Sigh.
    @volatile var ickyScalaApiWorkaround: Option[Process] = None

    def onOut(line: String): Unit = line match {
      case ProgressRegex(numerator, denominator) => {
        if (!onProgress(numerator.toInt, denominator.toInt)) {
          error = Some("You cancelled an import job")
          ickyScalaApiWorkaround.foreach(_.destroy)
        }
      }
      case s: String if s.trim.length > 0 && error.isEmpty => {
        error = Some(s.trim)
      }
      case _ => {}
    }

    def onErr(line: String): Unit = System.err.println(line) // So Logstash will pick up on it

    val process: Process = Process(cmd).run(ProcessLogger(onOut, onErr))
    ickyScalaApiWorkaround = Some(process)

    Future(blocking(process.exitValue)).map { retval =>
      if (retval != 0 && error.isEmpty) error = Some("PDF is valid, but a bug in Overview means we can't load it")

      error.toLeft(())
    }
  }

  private def command(in: Path, out: Path, lang: String): Seq[String] = Seq(
    PdfNormalizer.javaPath,
    "-Xmx" + Configuration.getString("pdf_memory"),
    "-cp", PdfNormalizer.classPath,
    "com.overviewdocs.helpers.MakeSearchablePdf",
    in.toString,
    out.toString,
    lang
  )
}

object PdfNormalizer extends PdfNormalizer {
  private val javaPath: String = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java").toString
  private val classPath: String = System.getProperty("java.class.path")

  override protected val logger = Logger.forClass(getClass)
}
