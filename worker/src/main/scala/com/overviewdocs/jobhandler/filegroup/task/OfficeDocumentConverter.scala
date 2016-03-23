package com.overviewdocs.jobhandler.filegroup.task

import java.nio.file.{Files,Path}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.jobhandler.filegroup.TaskIdPool
import com.overviewdocs.util.{Configuration,Logger}

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
  protected val taskIdPool: TaskIdPool
  protected val logger: Logger

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
    * Also, try to give LibreOffice a file with the correct extension. While
    * LibreOffice can correct for the wrong file extension, it clearly takes a
    * different code path for that. Best to do what the user expects.
    *
    * The caller should delete the file when finished with it.
    */
  def convertFileToPdf(in: Path)(implicit ec: ExecutionContext): Future[Path] = {
    // Predict where LibreOffice will place the output file.
    val outputPath = TempDirectory.path.resolve(basename(in.getFileName.toString) + ".pdf")

    Future(blocking {
      outputPath.toFile.delete // Probably a no-op

      val taskId: Int = taskIdPool.acquireId
      val tmpProfileDir: Path = TempDirectory.path.resolve(s"soffice-profile-${taskId}")
      val cmd: Seq[String] = command(in, tmpProfileDir)

      logger.info("Running Office command: {}", cmd.mkString(" "))

      val process = new ProcessBuilder(cmd: _*).inheritIO.start

      if (!process.waitFor(timeout.length, timeout.unit)) {
        process.destroyForcibly.waitFor // blocking -- laziness!

        outputPath.toFile.delete // if it exists

        // After we kill LibreOffice, its profile directory is wrecked. Delete
        // it; otherwise future invocations will time out. (This might be as
        // simple as a lockfile, I dunno ... but who cares?)
        rm_rf_sync(tmpProfileDir)

        taskIdPool.releaseId(taskId)
        throw new OfficeDocumentConverter.LibreOfficeTimedOutException
      }

      val retval = process.exitValue
      taskIdPool.releaseId(taskId)

      if (retval != 0) {
        outputPath.toFile.delete // if it exists
        throw new OfficeDocumentConverter.LibreOfficeFailedException(s"$libreOfficeLocation returned exit code $retval")
      }

      if (!outputPath.toFile.exists) {
        throw new OfficeDocumentConverter.LibreOfficeFailedException(s"$libreOfficeLocation didn't write to $outputPath")
      }

      outputPath
    })
  }

  /** Like "rm -rf": delete a file/directory and all sub-files/directories.
    *
    * This is synchronous, because we're lazy.
    */
  private def rm_rf_sync(path: Path): Unit = {
    import scala.collection.JavaConverters.iterableAsScalaIterableConverter

    if (Files.isDirectory(path)) {
      iterableAsScalaIterableConverter(Files.newDirectoryStream(path)).asScala
        .toSeq // [adamhooper 2016-03-23] I _think_ if we don't do this, iteration misses stuff on Linux
        .foreach(rm_rf_sync _)
    }

    Files.deleteIfExists(path)
  }

  /** Removes the last ".xxx" from a filename.
    *
    * This mimics LibreOffice:
    *
    * * Two extensions? Just remove the final one.
    * * Zero extensions? Leave as-is.
    */
  private def basename(filename: String): String = {
    val pos = filename.lastIndexOf('.')
    if (pos == -1) {
      filename
    } else {
      filename.substring(0, pos)
    }
  }

  private def command(in: Path, profileDir: Path): Seq[String] = Seq(
    libreOfficeLocation,
    "--headless",
    "--nologo",
    "--invisible",
    "--norestore",
    "--nolockcheck",
    "--convert-to", "pdf",
    "--outdir", TempDirectory.path.toString,
    s"-env:UserInstallation=file://${profileDir.toString}",
    in.toString
  )
}

object OfficeDocumentConverter extends OfficeDocumentConverter {
  override protected val libreOfficeLocation = Configuration.getString("libre_office_path")
  override protected val timeout = FiniteDuration(Configuration.getInt("document_conversion_timeout"), TimeUnit.MILLISECONDS)
  override protected val logger = Logger.forClass(getClass)
  override protected val taskIdPool = TaskIdPool()

  case class LibreOfficeFailedException(message: String) extends Exception(message)
  case class LibreOfficeTimedOutException() extends Exception("LibreOffice timed out")
}
