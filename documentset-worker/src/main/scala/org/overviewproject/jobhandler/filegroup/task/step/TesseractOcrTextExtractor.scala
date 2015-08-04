package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.io.Source
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.Exception.ultimately
import com.overviewdocs.jobhandler.filegroup.task.ShellRunner
import com.overviewdocs.jobhandler.filegroup.task.TimeoutGenerator
import com.overviewdocs.util.Configuration
import com.overviewdocs.util.SupportedLanguages

trait TesseractOcrTextExtractor extends OcrTextExtractor {
  implicit protected val executionContext: ExecutionContext
  protected val shellRunner: ShellRunner
  protected val ocrTimeout: FiniteDuration
  protected val tesseractLocation: String
  protected val fileSystem: FileSystem

  import TesseractOcrTextExtractor.FileFormats._

  protected trait FileSystem {
    def writeImage(image: BufferedImage): File
    def readText(file: File): String
    def deleteFile(file: File): Boolean
  }

  def extractText(image: BufferedImage, language: String): Future[String] =
    withImageAsTemporaryFile(image) { tempFile =>
      extractTextWithOcr(tempFile, language) { textTempFile =>
        fileSystem.readText(textTempFile)
      }
    }

  private def withImageAsTemporaryFile(image: BufferedImage)(f: File => Future[String]): Future[String] = {
    val storedImage = Future.fromTry(Try { fileSystem.writeImage(image) })

    def callAndDeleteWhenComplete(imageFile: File): Future[String] = 
      f(imageFile).andThen { case _ => fileSystem.deleteFile(imageFile) }


    for {
      imageFile <- storedImage
      result <- callAndDeleteWhenComplete(imageFile)
    } yield result
  }

  private def extractTextWithOcr(imageFile: File, language: String)(f: File => String): Future[String] = {
    // Tesseract needs language specified as a ISO639-2 code. 
    // A language parameter that does not have an appropriate transformation denotes an error
    // and an exception is thrown.
    val iso639_2Code = SupportedLanguages.asIso639_2(language).get

    val output = outputFile(imageFile)
    shellRunner.run(tesseractCommand(imageFile.getAbsolutePath, output.getAbsolutePath(), iso639_2Code), ocrTimeout)
      .map { _ =>
        ultimately(fileSystem.deleteFile(output)) {
          f(output)
        }
      }

  }

  private def tesseractCommand(inputFile: String, outputFile: String, language: String): String = {
    val outputBase = outputFile.replace(s".$TextOutput", "")
    s"$tesseractLocation $inputFile $outputBase -l $language -psm 1"
  }

  private def outputFile(inputFile: File): File = {
    val inputFilePath = inputFile.getAbsolutePath
    val outputFilePath = inputFilePath.replace(s".$ImageFormat", s".$TextOutput")
    new File(outputFilePath)
  }

}

object TesseractOcrTextExtractor {

  import scala.concurrent.duration.DurationInt
  import scala.language.postfixOps
  import com.overviewdocs.util.Configuration

  object FileFormats {
    val ImageFormat = "png"
    val TextOutput = "txt"
  }

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
        val imageFile = File.createTempFile("overview-ocr", s".${FileFormats.ImageFormat}")

        ImageIO.write(image, FileFormats.ImageFormat, imageFile)

        imageFile
      }

      override def readText(textFile: File): String =
        Source.fromFile(textFile).mkString

      override def deleteFile(file: File): Boolean = file.delete
    }
  }
}