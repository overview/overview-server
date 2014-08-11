package org.overviewproject.jobhandler.filegroup.task

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption._
import java.util.UUID
import scala.util.control.Exception._
import org.overviewproject.util.Configuration
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Converts an [[InputStream]] to a PDF using LibreOffice.
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
  /** The call to `LibreOffice` resulted in an error code (including if no
    * `soffice` binary is found).
    */
  case class LibreOfficeConverterFailedException(reason: String) extends Exception(reason)

  /** Conversion succeeded, but no output file was found.
    *
    * The most likely reason: another instance of LibreOffice is running.
    */
  case class LibreOfficeNoOutputException(reason: String) extends Exception(reason)

  private val LibreOfficeLocation = Configuration.getString("libre_office_path")
  private val OutputFileExtension = "pdf"

  override def withStreamAsPdf[T](guid: UUID, filename: String, inputStream: InputStream)(f: InputStream => T): T = {
    withStreamAsTemporaryFile(guid, inputStream) { tempFile =>
      convertFileToPdf(tempFile) { pdfTempFile =>
        withFileAsStream(pdfTempFile)(f)
      }
    }
  }

  /** Writes inputStream to a temporary file, calls `f`, and deletes it.
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
  protected def withStreamAsTemporaryFile[T](guid: UUID, inputStream: InputStream)(f: File => T): T = {
    val inputFilePath = TempDirectory.filePath(guid.toString())
    val input = fileSystem.saveToFile(inputStream, inputFilePath)

    ultimately(fileSystem.deleteFile(input)) {
      f(inputFilePath.toFile)
    }
  }

  // Calls LibreOffice with the given inputFile as input parameter
  // If the call succeeds, f is called with the resulting output file as a parameter.
  // The output file is deleted after the call to f
  // If the call fails a LibreOfficeConverterFailed exception is thrown
  private def convertFileToPdf[T](inputFile: File)(f: File => T): T = {
    val officeCommand = conversionCommand(inputFile.getAbsolutePath)

    val result: Either[String, String] = runner.run(officeCommand)
    result.fold(e => throw new LibreOfficeConverterFailedException(e), { s =>
      val output = outputFile(inputFile)

      ultimately(fileSystem.deleteFile(output)) {
        f(outputFile(inputFile))
      }
    })
  }

  // reads the given outputFile as a stream, passed to f
  // Closes the stream after call to f
  // If output file does not exist, a LibreOfficeNoOutput exception is thrown
  private def withFileAsStream[T](file: File)(f: InputStream => T): T = {
    val detectingNoFile = handling(classOf[FileNotFoundException]) by { e => throw LibreOfficeNoOutputException(e.getMessage) }
    def fileStream = detectingNoFile { fileSystem.readFile(file) }

    ultimately(fileStream.close) {
      f(fileStream)
    }
  }

  private def conversionCommand(inputFile: String): String =
    s"$LibreOfficeLocation --headless --nologo --invisible --norestore --nolockcheck --convert-to pdf --outdir ${TempDirectory.path} $inputFile"

  private def outputFile(inputFile: File): File = {
    val inputName = inputFile.getName
    val outputName = s"$inputName.$OutputFileExtension"

    TempDirectory.filePath(outputName).toFile
  }

  protected val runner: LibreOfficeDocumentConverter.Runner
  protected val fileSystem: LibreOfficeDocumentConverter.FileSystem
}

/** Implements [[DocumentConverter]] with components that perform filesystem and command runner functions */
object LibreOfficeDocumentConverter extends LibreOfficeDocumentConverter {
  import scala.language.postfixOps
  import scala.concurrent.duration._
  import scala.concurrent.TimeoutException

  override protected val runner: Runner = ShellRunner
  override protected val fileSystem: FileSystem = OsFileSystem

  trait Runner {
    def run(command: String): Either[String, String]
  }

  trait FileSystem {
    def saveToFile(inputStream: InputStream, filePath: Path): File
    def readFile(file: File): InputStream
    def deleteFile(file: File): Boolean
  }

  object OsFileSystem extends FileSystem {
    override def saveToFile(inputStream: InputStream, filePath: Path): File = {
      Files.copy(inputStream, filePath, REPLACE_EXISTING)

      filePath.toFile
    }

    override def readFile(file: File): InputStream = new FileInputStream(file)
    override def deleteFile(file: File): Boolean = file.delete
  }

  object ShellRunner extends Runner {
    private val ConversionTimeout = Configuration.getInt("document_conversion_timeout") millis 
    private val TimeoutErrorMessage = "Conversion timeout exceeded"

    override def run(command: String): Either[String, String] = {
      def cancellingProcessAfterTimeout(process: RunningCommand) =
        handling(classOf[TimeoutException]) by { _ =>
          process.cancel
          Left(TimeoutErrorMessage)
        }

      val commandRunner = new CommandRunner(command)

      val runningCommand = commandRunner.runAsync

      cancellingProcessAfterTimeout(runningCommand) {
        Await.result(runningCommand.result, ConversionTimeout)
      }
    }
  }
}
