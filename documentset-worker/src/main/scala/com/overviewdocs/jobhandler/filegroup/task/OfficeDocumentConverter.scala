package com.overviewdocs.jobhandler.filegroup.task

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.util.Configuration

/** Converts a file to PDF using LibreOffice.
  *
  * The location of `loffice` must be specified by the `LIBRE_OFFICE_PATH`
  * environment variable. This implementation writes to a temp directory that
  * it destroys when finished.
  *
  * If another instance of LibreOffice (including quick-starter) is running
  * on the same system, the conversion will fail.
  */
trait OfficeDocumentConverter {
  protected val libreOfficeLocation: String
  protected val timeout: FiniteDuration

  /** Runs the actual conversion, writing to a temporary file.
    *
    * Will return a Future[Unit], but there are errors you should anticipate:
    *
    * * `LibreOfficeTimedOutException`: LibreOffice ran too long.
    * * `LibreOfficeFailedException`: LibreOffice did not return exit code 0,
    *                                 or it didn't produce any output.
    *
    * Ensure the basename (i.e., filename without suffix) of `in` is unique
    * across all calls ever. That's because LibreOffice outputs all files into
    * a single directory, and we don't want to deal with the hassle of creating
    * a directory per call.
    *
    * The caller should delete the file when finished with it.
    */
  def convertFileToPdf(in: Path)(implicit ec: ExecutionContext): Future[Path] = {
    // Predict where LibreOffice will place the output file.
    val outputPath = TempDirectory.path.resolve(in.getFileName.toString + ".pdf")

    Future(blocking {
      val process = new ProcessBuilder(command(in): _*).inheritIO.start

      if (!process.waitFor(timeout.length, timeout.unit)) {
        process.destroyForcibly.waitFor
        outputPath.toFile.delete // if it exists
        throw new OfficeDocumentConverter.LibreOfficeTimedOutException
      }

      val retval = process.exitValue
      if (retval != 0) {
        outputPath.toFile.delete // if it exists
        throw new OfficeDocumentConverter.LibreOfficeFailedException(s"$libreOfficeLocation returned exit code $retval")
      }

      if (!outputPath.toFile.exists) {
        throw new OfficeDocumentConverter.LibreOfficeFailedException(s"$libreOfficeLocation didn't output a file")
      }

      outputPath
    })
  }

  private def command(in: Path): Seq[String] = Seq(
    libreOfficeLocation,
    "--headless",
    "--nologo",
    "--invisible",
    "--norestore",
    "--nolockcheck",
    "--convert-to",
    "pdf",
    "--outdir",
    TempDirectory.path.toString,
    in.toString
  )
}

object OfficeDocumentConverter extends OfficeDocumentConverter {
  override protected val libreOfficeLocation = Configuration.getString("libre_office_path")
  override protected val timeout = FiniteDuration(Configuration.getInt("document_conversion_timeout"), TimeUnit.MILLISECONDS)

  case class LibreOfficeFailedException(message: String) extends Exception(message)
  case class LibreOfficeTimedOutException() extends Exception("LibreOffice timed out")
}
