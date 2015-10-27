package com.overviewdocs.jobhandler.filegroup.task

import java.io.{ BufferedInputStream, File, FileInputStream, FileNotFoundException, InputStream }
import java.nio.file.{Files,Path}
import java.nio.file.StandardCopyOption._
import java.util.UUID
import scala.util.control.Exception._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.util.Configuration

/**
 * Converts an [[InputStream]] to a PDF using LibreOffice.
 *
 * The location of `LibreOffice` must be specified by the `LIBRE_OFFICE_PATH`
 * environment variable, or directly in the worker configuration file. Files
 * created during the conversion are written to a temp directory and are
 * deleted when the conversion is complete.
 *
 * If another instance of `LibreOffice` (including quick-starter) is running
 * on the same system, the conversion will fail.
 */
trait LibreOfficeDocumentConverter extends DocumentConverter {
  implicit protected val executionContext: ExecutionContext
  protected val conversionTimeout: FiniteDuration
  protected val runner: ShellRunner
  protected val fileSystem: LibreOfficeDocumentConverter.FileSystem

  /**
   * The call to `LibreOffice` resulted in an error code (including if no
   * `soffice` binary is found).
   */
  case class LibreOfficeConverterFailedException(reason: String) extends Exception(reason)

  private val LibreOfficeLocation = Configuration.getString("libre_office_path")
  private val OutputFileExtension = "pdf"

  override def withStreamAsPdf[T](guid: UUID, inputStream: InputStream)(f: (InputStream, Long) => Future[T]): Future[T] = {
    withStreamAsTemporaryFile(guid, inputStream) { tempFile =>
      convertFileToPdfAndThen(tempFile) { pdfTempFile =>
        withFileAsStream(pdfTempFile)(f)
      }
    }
  }

  /**
   * Writes inputStream to a temporary file, calls `f`, and deletes it.
   *
   * The temporary file will be named "guid". LibreOffice does not seem to
   * care about file extensions, and it does not let us specify an output
   * filename; we use "guid" because we know LibreOffice will output
   * "guid.pdf".
   *
   * @param guid A unique ID for creating temporary files.
   * @param inputStream The input stream.
   * @param f A function that will be passed the filename.
   * @tparam T The return type of `f`
   *
   * @returns The return value of `f`
   * @throws IOException if the file cannot be written
   * @throws Exception if `f` throws an exception
   */
  protected def withStreamAsTemporaryFile[T](guid: UUID, inputStream: InputStream)(f: File => Future[T]): Future[T] = {
    val inputFilePath = TempDirectory.filePath(guid.toString())
    val input = fileSystem.saveToFile(inputStream, inputFilePath)

    val result = f(inputFilePath.toFile)
    result.andThen { case _ => fileSystem.deleteFile(input) }
  }

  // Calls LibreOffice with the given inputFile as input parameter
  // If the call succeeds, f is called with the resulting output file as a parameter.
  // The output file is deleted after the call to f
  private def convertFileToPdfAndThen[T](inputFile: File)(f: File => Future[T]): Future[T] = {
    val output = outputFile(inputFile)

    convertFileToPdf(inputFile.toPath, output.toPath)
      .flatMap { _ => f(output) }
      .andThen { case _ => Future(blocking(fileSystem.deleteFile(output))) }
  }

  override def convertFileToPdf(in: Path, out: Path): Future[Unit] = {
    val officeCommand = conversionCommand(in.toString)
    val outputPath = outputFile(in.toFile).toPath

    for {
      _ <- runner.run(officeCommand, conversionTimeout)
      _ <- Future(blocking(fileSystem.moveFile(outputPath, out)))
    } yield ()
  }

  // reads the given outputFile as a stream, passed to f
  // Closes the stream after call to f
  // If output file does not exist, a LibreOfficeNoOutput exception is thrown
  private def withFileAsStream[T](file: File)(f: (InputStream, Long) => Future[T]): Future[T] = {
    val detectingNoFile = handling(classOf[FileNotFoundException]) by { e =>
      throw LibreOfficeDocumentConverter.LibreOfficeNoOutputException(e.getMessage)
    }
    val fileStream = detectingNoFile { fileSystem.readFile(file) }
    val fileLength = detectingNoFile { fileSystem.getFileLength(file) }


    val result = f(fileStream, fileLength)
    result.onComplete(_ => fileStream.close)

    result
  }

  private def conversionCommand(inputFile: String): String = {
    s"$LibreOfficeLocation --headless --nologo --invisible --norestore --nolockcheck --convert-to pdf --outdir ${TempDirectory.path} $inputFile"
  }

  private def outputFile(inputFile: File): File = {
    val inputName = inputFile.getName
    val outputName = s"$inputName.$OutputFileExtension"

    TempDirectory.filePath(outputName).toFile
  }

}

/** Implements [[DocumentConverter]] with components that perform filesystem and command runner functions */
object LibreOfficeDocumentConverter {
  trait FileSystem {
    def saveToFile(inputStream: InputStream, filePath: Path): File
    def readFile(file: File): InputStream
    def getFileLength(file: File): Long
    def deleteFile(file: File): Boolean
    def moveFile(from: Path, to: Path): Unit
  }

  /**
   * Conversion succeeded, but no output file was found.
   *
   * The most likely reason: another instance of LibreOffice is running.
   */
  case class LibreOfficeNoOutputException(reason: String) extends Exception(reason)

  def apply(timeoutGenerator: TimeoutGenerator)(implicit executionContext: ExecutionContext): DocumentConverter =
    new LibreOfficeDocumentConverterImpl(timeoutGenerator, executionContext)

  private class LibreOfficeDocumentConverterImpl(
    timeoutGenerator: TimeoutGenerator,
    override implicit protected val executionContext: ExecutionContext) extends LibreOfficeDocumentConverter {

    import scala.language.postfixOps
    import scala.concurrent.duration._
    import scala.concurrent.TimeoutException

    override protected val runner = ShellRunner(timeoutGenerator)
    override protected val fileSystem: FileSystem = OsFileSystem
    override protected val conversionTimeout = Configuration.getInt("document_conversion_timeout") millis

    object OsFileSystem extends FileSystem {
      override def saveToFile(inputStream: InputStream, filePath: Path): File = {
        val bufferedInputStream = new BufferedInputStream(inputStream, 5 * 1024 * 1024)
        Files.copy(bufferedInputStream, filePath, REPLACE_EXISTING)

        filePath.toFile
      }

      override def readFile(file: File): InputStream = new FileInputStream(file)
      override def getFileLength(file: File): Long = file.length
      override def moveFile(from: Path, to: Path): Unit = Files.move(from, to)
      override def deleteFile(file: File): Boolean = file.delete
    }

  }
}
