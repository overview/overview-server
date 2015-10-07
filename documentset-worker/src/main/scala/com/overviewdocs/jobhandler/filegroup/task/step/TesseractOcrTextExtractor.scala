package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import java.nio.file.{Files,Path}
import javax.imageio.ImageIO
import scala.concurrent.{ExecutionContext,Future,blocking}
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.sys.process.{Process,ProcessLogger}

import com.overviewdocs.jobhandler.filegroup.task.CommandFailedException
import com.overviewdocs.util.Configuration
import com.overviewdocs.util.SupportedLanguages

trait TesseractOcrTextExtractor extends OcrTextExtractor {
  protected val fileSystem: TesseractOcrTextExtractor.FileSystem

  override def extractText(image: BufferedImage, language: String)(implicit ec: ExecutionContext): Future[String] = {
    withImageAsTemporaryFile(image) { tempPath =>
      extractTextWithOcr(tempPath, language) { textTempPath =>
        fileSystem.readText(textTempPath)
      }
    }
  }

  private def withImageAsTemporaryFile(image: BufferedImage)(f: Path => Future[String])(implicit ec: ExecutionContext): Future[String] = {
    Future(blocking(fileSystem.writeImage(image)))
      .flatMap { imagePath =>
        f(imagePath).andThen { case _ => fileSystem.deleteFile(imagePath) }
      }
  }

  private def extractTextWithOcr(imagePath: Path, language: String)(f: Path => String)(implicit ec: ExecutionContext): Future[String] = {
    // Tesseract needs language specified as a ISO639-2 code. 
    // A language parameter that does not have an appropriate transformation denotes an error
    // and an exception is thrown.
    val iso639_2Code = SupportedLanguages.asIso639_2(language).get

    val basename = imagePath.getFileName.toString
    val basenameNoExtension = basename.substring(0, basename.length - 4)

    val output: Path = imagePath.resolveSibling(s"$basename.txt")
    val outputWithoutExtension: Path = imagePath.resolveSibling(basename)

    val consoleOutput = new StringBuilder()

    val process = Process(Seq(
      TesseractOcrTextExtractor.tesseractLocation,
      imagePath.toAbsolutePath.toString,
      outputWithoutExtension.toAbsolutePath.toString,
      "-l",
      iso639_2Code,
      "-psm",
      "1"
    )).run(ProcessLogger(s => consoleOutput.append(s), s => consoleOutput.append(s)))

    Future(blocking(process.exitValue))
      .map { retval =>
        if (retval == 0) throw new CommandFailedException(consoleOutput.toString)
        f(output)
      }
      .andThen { case _ => fileSystem.deleteFile(output) }
  }
}

object TesseractOcrTextExtractor extends TesseractOcrTextExtractor {
  trait FileSystem {
    def writeImage(image: BufferedImage): Path
    def readText(path: Path): String
    def deleteFile(path: Path): Unit
  }

  val ImageResolution = 400 // dpi
  lazy val tesseractLocation = Configuration.getString("tesseract_path")

  override protected val fileSystem = new FileSystem {
    override def writeImage(image: BufferedImage) = {
      val imagePath: Path = Files.createTempFile("overview-ocr-", ".png")
      ImageFileWriter.writeImage(image, imagePath.toFile, "png", ImageResolution)
      imagePath
    }

    override def readText(path: Path) = iterableAsScalaIterable(Files.readAllLines(path)).mkString
    override def deleteFile(path: Path) = Files.delete(path)
  }
}
