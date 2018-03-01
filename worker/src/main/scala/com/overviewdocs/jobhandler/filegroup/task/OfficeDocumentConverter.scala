package com.overviewdocs.jobhandler.filegroup.task

import java.nio.file.{Files,Path}
import java.util.concurrent.TimeUnit
import scala.collection.mutable
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
  private val libreOfficeLocation = "/usr/lib/libreoffice/program/soffice.bin"
  protected val timeout: FiniteDuration
  protected val taskIdPool: TaskIdPool
  protected val logger: Logger

  private lazy val initializedTempProfileDirs: mutable.Set[Path] = mutable.Set.empty[Path]

  private def rm_rf(path: Path)(implicit ec: ExecutionContext): Future[Unit] = Future(blocking {
    if (Files.exists(path)) {
      logger.info("Clearing directory: {}", path)
      val paths = Files.walk(path).toArray(n => Array.fill[Path](n)(null)).toVector.reverse
      paths.foreach(Files.delete _)
    }
  })

  /** Ensures there's a valid profile directory.
    *
    * LibreOffice's "soffice.bin" fails with exit code 81 if the given directory
    * does not exist -- _and_ it creates it. We want to run this once per
    * directory per invocation of Overview, before any conversions.
    *
    * This method isn't thread-safe: it assumes nobody else uses taskId until
    * it finishes.
    */
  private def getTempProfileDir(taskId: Int)(implicit ec: ExecutionContext): Future[Path] = {
    val ret: Path = TempDirectory.path.resolve(s"soffice-profile-${taskId}")
    if (initializedTempProfileDirs.contains(ret)) {
      Future.successful(ret)
    } else {
      for {
        _ <- initTempProfileDir(ret)
      } yield {
        initializedTempProfileDirs.+=(ret)
        ret
      }
    }
  }

  private def initTempProfileDir(path: Path)(implicit ec: ExecutionContext): Future[Unit] = {
    // To be consistent across restarts, dev and unit test, we `rm -rf` the
    // directory before returning it. That means that when getTempProfileDir
    for {
      _ <- rm_rf(path)
      _ <- runLibreOfficeToInitializeProfileDirectory(path)
    } yield ()
  }

  /** Deletes a profile directory and un-caches it.
    *
    * Call this after LibreOffice fails: otherwise it will be locked and/or try
    * to recover.
    */
  private def resetTempProfileDir(path: Path)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- rm_rf(path)
      _ <- runLibreOfficeToInitializeProfileDirectory(path)
    } yield ()
  }

  private def runLibreOfficeToInitializeProfileDirectory(path: Path)(implicit ec: ExecutionContext): Future[Unit] = {
    val cmd = command(path)
    logger.info("Initializing Office directory: {}", cmd.mkString(" "))
    Future(blocking {
      val process = new ProcessBuilder(cmd: _*).inheritIO.start
      process.waitFor match {
        case 81 => () // this is what we want on empty profile dir: https://bugs.documentfoundation.org/show_bug.cgi?id=107912
        case retval: Int => throw new OfficeDocumentConverter.LibreOfficeFailedException(s"$libreOfficeLocation returned exit code $retval while initializing profile directory. (We expected 81.)")
      }
    })
  }

  /** Runs the actual conversion, writing to a temporary file.
    *
    * Will return a Future[Path] of a new file on disk that the caller must
    * delete.
    *
    * Failures you should anticipate:
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

    val taskId: Int = taskIdPool.acquireId

    getTempProfileDir(taskId)
      .flatMap(tmpProfileDir => Future(blocking {
        outputPath.toFile.delete // Probably a no-op
        val cmd: Seq[String] = command(tmpProfileDir) ++ Seq(in.toString)

        logger.info("Running Office command: {}", cmd.mkString(" "))

        val process = new ProcessBuilder(cmd: _*).inheritIO.start

        if (!process.waitFor(timeout.length, timeout.unit)) {
          process.destroyForcibly.waitFor // blocking -- laziness!

          outputPath.toFile.delete // if it exists

          // After we kill LibreOffice, its profile directory is wrecked. Delete
          // it; otherwise future invocations will time out. (This might be as
          // simple as a lockfile, I dunno ... but who cares?)
          resetTempProfileDir(tmpProfileDir).onComplete(_ => taskIdPool.releaseId(taskId))
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
      }))
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

  private def command(profileDir: Path): Seq[String] = Seq(
    libreOfficeLocation,
    "--quickstart=no",
    "--headless",
    "--nologo",
    "--invisible",
    "--norestore",
    "--nolockcheck",
    "--convert-to", "pdf",
    "--outdir", TempDirectory.path.toString,
    s"-env:UserInstallation=file://${profileDir.toString}"
  )
}

object OfficeDocumentConverter extends OfficeDocumentConverter {
  override protected val timeout = FiniteDuration(Configuration.getInt("document_conversion_timeout"), TimeUnit.MILLISECONDS)
  override protected val logger = Logger.forClass(getClass)
  override protected val taskIdPool = TaskIdPool()

  case class LibreOfficeFailedException(message: String) extends Exception(message)
  case class LibreOfficeTimedOutException() extends Exception("LibreOffice timed out")
}
