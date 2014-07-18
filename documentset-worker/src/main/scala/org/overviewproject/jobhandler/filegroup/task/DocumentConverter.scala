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

/**
 * Converts the content of a [[InputStream]] to a PDF [[InputStream]] using `LibreOffice`.
 * The location of `LibreOffice` must be specified by the `LIBRE_OFFICE_PATH` environment variable,
 * or directly in the worker configuration file.
 * Files created during the conversion are written to a temp directory, and are deleted when the conversion is
 * complete, if possible.
 *
 * If another instance of `LibreOffice` (including quick-starter) is running on the same system, the conversion will fail.
 */
trait DocumentConverter {

  /**
   *  Exception thrown if the call to `LibreOffice` resulted in an error code
   *  (including if no `soffice` binary was found).
   */
  case class ConverterFailedException(reason: String) extends Exception(reason)

  /**
   * Exception thrown if conversion completed with no error code, but no output file was found.
   * The most likely reason is that another instance of `LibreOffice` is running.
   */
  case class NoConverterOutputException(reason: String) extends Exception(reason)

  private val LibreOfficeLocation = Configuration.getString("libre_office_path")
  private val OutputFileExtension = "pdf"

  /**
   * Converts the given `inputStream` into a PDF [[InputStream]].
   * @param guid A unique id used to create working file filenames
   * @param inputStream The document data to be converted
   * @param f A function that will be passed an [[InputStream]] containing the converted PDF document
   * @tparam T the return type of `f`
   *
   * @returns the return value of `f`
   * @throws ConverterFailedException If the call to `LibreOffice` resulted in an error
   * @throws NoConverterOutputException If the conversion completed with no error, but no output was generated
   */
  def convertToPdfStream[T](guid: UUID, inputStream: InputStream)(f: InputStream => T): T = {

    writeStreamToInputFile(inputName(guid), inputStream) { filename =>
      convertInputToPdfFile(filename) { pdfFileName =>
        readOutputFile(pdfFileName)(f)
      }
    }
  }

  private def inputName(guid: UUID): String = guid.toString

  // Writes the content of the inputStream into file named inputFileName, in TempDirectory
  // Calls f with the input file as a parameter
  // The input file is deleted after the call to f
  // Exceptions are not caught, and are assumed to be handled by caller
  private def writeStreamToInputFile[T](inputFileName: String, inputStream: InputStream)(f: File => T): T = {
    val inputFilePath = TempDirectory.filePath(inputFileName)
    val input = fileSystem.saveToFile(inputStream, inputFilePath)

    ultimately(fileSystem.deleteFile(input)) {
      f(inputFilePath.toFile)
    }
  }

  // Calls LibreOffice with the given inputFile as input parameter
  // If the call succeeds, f is called with the resulting output file as a parameter.
  // The output file is deleted after the call to f
  // If the call fails a ConverterFailed exception is thrown
  private def convertInputToPdfFile[T](inputFile: File)(f: File => T): T = {
    val officeCommand = conversionCommand(inputFile.getAbsolutePath)

    val result: Either[String, String] = runner.run(officeCommand)
    result.fold(e => throw new ConverterFailedException(e), { s =>
      val output = outputFile(inputFile)

      ultimately(fileSystem.deleteFile(output)) {
        f(outputFile(inputFile))
      }
    })
  }

  // reads the given outputFile as a stream, passed to f
  // Closes the stream after call to f
  // If output file does not exist, a NoConverterOutput exception is thrown
  private def readOutputFile[T](outputFile: File)(f: InputStream => T): T = {
    val detectingNoFile = handling(classOf[FileNotFoundException]) by { e => throw NoConverterOutputException(e.getMessage) }
    def fileStream = detectingNoFile { fileSystem.readFile(outputFile) }

    ultimately(fileStream.close) {
      f(fileStream)
    }
  }

  private def conversionCommand(inputFile: String): String =
    s"$LibreOfficeLocation --headless --invisible --norestore --nolockcheck --convert-to pdf --outdir ${TempDirectory.path} $inputFile"

  private def outputFile(inputFile: File): File = {
    val inputName = inputFile.getName
    val outputName = s"$inputName.$OutputFileExtension"

    TempDirectory.filePath(outputName).toFile
  }

  protected val runner: Runner
  protected val fileSystem: FileSystem

  protected trait Runner {
    def run(command: String): Either[String, String]
  }

  protected trait FileSystem {
    def saveToFile(inputStream: InputStream, filePath: Path): File
    def readFile(file: File): InputStream
    def deleteFile(file: File): Boolean
  }
}

/** Implements [[DocumentConverter]] with components that perform filesystem and command runner functions */
object DocumentConverter extends DocumentConverter {
  import scala.language.postfixOps
  import scala.concurrent.duration._
  import scala.concurrent.TimeoutException

  override protected val runner: Runner = new ShellRunner
  override protected val fileSystem: FileSystem = new DefaultFileSystem

  class ShellRunner extends Runner {
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

  class DefaultFileSystem extends FileSystem {
    override def saveToFile(inputStream: InputStream, filePath: Path): File = {
      Files.copy(inputStream, filePath, REPLACE_EXISTING)

      filePath.toFile
    }

    override def readFile(file: File): InputStream = new FileInputStream(file)
    override def deleteFile(file: File): Boolean = file.delete
  }
}
