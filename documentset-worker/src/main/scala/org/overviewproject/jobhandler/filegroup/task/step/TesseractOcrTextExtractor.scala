package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.ShellRunner
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import scala.io.Source
import java.io.File
import scala.util.control.Exception.ultimately

trait TesseractOcrTextExtractor {
  implicit protected val executionContext: ExecutionContext
  protected val shellRunner: ShellRunner
  protected val ocrTimeout: FiniteDuration
  protected val tesseractLocation: String
  protected val fileSystem: FileSystem

  protected trait FileSystem {
    def writeImage(image: BufferedImage): File
    def readText(file: File): String
    def deleteFile(file: File): Boolean
  }

  def extractText(image: BufferedImage, language: String): Future[String] = {

    val result = withImageAsTemporaryFile(image) { tempFile =>
      extractTextWithOcr(tempFile, language) { textTempFile =>
        fileSystem.readText(textTempFile)
      }
    }

    result
  }

  private def withImageAsTemporaryFile(image: BufferedImage)(f: File => Future[String]): Future[String] = {
    val imageFile = fileSystem.writeImage(image)

    ultimately(fileSystem.deleteFile(imageFile)) {
      f(imageFile)
    }
  }

  private def extractTextWithOcr(imageFile: File, language: String)(f: File => String): Future[String] = {
    
    val output = outputFile(imageFile)
    shellRunner.run(tesseractCommand(imageFile.getAbsolutePath, output.getAbsolutePath(), language), ocrTimeout)
      .map { _ =>
        ultimately(fileSystem.deleteFile(output)) {
          f(output)
        }
      }

  }

  private def tesseractCommand(inputFile: String, outputFile: String, language: String): String =
    s"$tesseractLocation $inputFile $outputFile -l $language"

  private def outputFile(inputFile: File): File = {
    val inputFilePath = inputFile.getAbsolutePath
    val outputFilePath = inputFilePath.replace(".png", ".txt")
    new File(outputFilePath)
  }

}

object TesseractOcrTextExtractor {

  import scala.concurrent.duration.DurationInt
  import scala.language.postfixOps
  import org.overviewproject.util.Configuration

  def apply(timeoutGenerator: TimeoutGenerator)(implicit executionContext: ExecutionContext): TesseractOcrTextExtractor =
    new TesseractOcrTextExtractorImpl(ShellRunner(timeoutGenerator), executionContext)

  private class TesseractOcrTextExtractorImpl(
    override protected val shellRunner: ShellRunner,
    override implicit protected val executionContext: ExecutionContext) extends TesseractOcrTextExtractor {

    override protected val ocrTimeout = Configuration.getInt("ocr_timeout") millis
    override protected val fileSystem: FileSystem = new OsFileSystem

    override protected val tesseractLocation = Configuration.getString("tesseract_path")

    private class OsFileSystem extends FileSystem {
      override def writeImage(image: BufferedImage): File = {
        val imageFile = File.createTempFile("overview-ocr", ".png")
        ImageIO.write(image, "png", imageFile)

        imageFile
      }

      override def readText(textFile: File): String =
        Source.fromFile(textFile).mkString

      override def deleteFile(file: File): Boolean = file.delete
    }
  }
}