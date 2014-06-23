package org.overviewproject.jobhandler.filegroup.task

import java.util.UUID
import java.io.InputStream
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption._
import java.nio.file.Path
import java.io.File
import scala.util.control.Exception._
import java.nio.file.Paths

trait DocumentConverter {
  private val LibreOfficeLocation = "/Applications/LibreOffice.app/Contents/MacOS/soffice"
  private val TempDirectory = "overview-documentset-worker"
  private val OutputFileExtension = "pdf"

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
    val inputFilePath = tempFilePath(inputFileName)
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
    result.fold(e => throw new Exception(e), { s =>
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
    val fileStream = fileSystem.readFile(outputFile)

    ultimately(fileStream.close) {
      f(fileStream)
    }
  }

  private def conversionCommand(inputFile: String): String =
    s"$LibreOfficeLocation --headless --invisible --norestore --nolockcheck --convert-to pdf --outdir $tempDirectory $inputFile"

  // TODO: throw exception if property not found
  private def tempDirectory: Path = {
    val tmpDir = System.getProperty("java.io.tmpdir")

    Paths.get(tmpDir, TempDirectory)
  }

  private def tempFilePath(fileName: String): Path = {
    val tmpDir = System.getProperty("java.io.tmpdir")
    Paths.get(tmpDir, TempDirectory, fileName)

  }

  private def outputFile(inputFile: File): File = {
    val inputName = inputFile.getName
    val outputName = s"$inputName.$OutputFileExtension"

    tempFilePath(outputName).toFile
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

object DocumentConverter extends DocumentConverter {

  override protected val runner: Runner = new ShellRunner
  override protected val fileSystem: FileSystem = new DefaultFileSystem

  class ShellRunner extends Runner {
    override def run(command: String): Either[String, String] = {
      val commandRunner = new CommandRunner(command)
      commandRunner.run
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
