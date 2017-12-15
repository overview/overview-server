package com.overviewdocs.pdfocr

import java.nio.file.{Files,Path}
import java.io.{BufferedInputStream,InputStream}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext,Future,blocking}

class SplitPdfAndExtractTextReader(
  val parser: SplitPdfAndExtractTextParser,
  val tempDir: Path // TODO nix tempDir: stream results without caching them
) {
  import SplitPdfAndExtractTextParser.Token
  import SplitPdfAndExtractTextReader.{ReadOneResult,ReadAllResult}

  private var nPages: Option[Int] = None
  private var pageIndex: Int = 0
  private var currentThumbnailPath: Option[Path] = None
  private var currentPdfPath: Option[Path] = None

  private def deletePathIfExists(maybePath: Option[Path])(implicit ec: ExecutionContext): Future[Unit] = {
    maybePath match {
      case Some(path) => Future(blocking { Files.delete(path); () })
      case None => Future.successful(())
    }
  }

  private def pageError(message: String)(implicit ec: ExecutionContext): Future[ReadOneResult.Error] = {
    for {
      _ <- deletePathIfExists(currentThumbnailPath)
      _ <- deletePathIfExists(currentPdfPath)
    } yield ReadOneResult.Error(message)
  }

  private def readNPages(implicit ec: ExecutionContext): Future[Either[String,Int]] = {
    nPages match {
      case None => parser.next.map(_ match {
        case Token.Header(newNPages) => {
          nPages = Some(newNPages)
          Right(newNPages)
        }
        case Token.PdfError(message) => Left(message)
        case Token.InvalidInput(message) => Left(message)
        case _ => Left("Error in parser")
      })
      case Some(nPages) => Future.successful(Right(nPages))
    }
  }

  private def readPageOrFooter(nPages: Int)(implicit ec: ExecutionContext): Future[ReadOneResult] = {
    parser.next.flatMap(_ match {
      case Token.Page(isOcr, text) => {
        Future.successful(ReadOneResult.Page(
          pageIndex + 1,
          nPages,
          isOcr,
          text,
          currentPdfPath,
          currentThumbnailPath
        ))
      }
      case Token.PageThumbnail(nBytes, subStream) => {
        val filename = tempDir.resolve(s"p${pageIndex + 1}.png")
        currentThumbnailPath = Some(filename)
        Future(blocking {
          Files.copy(subStream, filename)
        }).flatMap(_ => readPageOrFooter(nPages)) // recurse
      }
      case Token.PagePdf(nBytes, subStream) => {
        val filename = tempDir.resolve(s"p${pageIndex + 1}.pdf")
        currentPdfPath = Some(filename)
        Future(blocking {
          Files.copy(subStream, filename)
        }).flatMap(_ => readPageOrFooter(nPages)) // recurse
      }
      case Token.PdfError(message) => pageError(message)
      case Token.InvalidInput(message) => pageError(message)
      case Token.Success => Future.successful(ReadOneResult.NoMorePages)
      case _ => pageError("Error in parser")
    })
  }

  def readOne(implicit ec: ExecutionContext): Future[ReadOneResult] = {
    currentThumbnailPath = None
    currentPdfPath = None
    readNPages.flatMap(_ match {
      case Left(error) => Future.successful(ReadOneResult.Error(error))
      case Right(nPages) => readPageOrFooter(nPages).map(v => { pageIndex += 1; v })
    })
  }

  def readAll(onProgress: (Int,Int) => Boolean)(implicit ec: ExecutionContext): Future[ReadAllResult] = {
    val pages = ArrayBuffer.empty[ReadOneResult.Page]

    def cancel(message: String): Future[ReadAllResult] = {
      Future(blocking {
        val paths = pages.flatMap(_.thumbnailPath) ++ pages.flatMap(_.pdfPath)
        paths.foreach(Files.deleteIfExists _)
        ReadAllResult.Error(message)
      })
    }

    def step: Future[ReadAllResult] = {
      readOne.flatMap(_ match {
        case page: ReadOneResult.Page => {
          pages.+=(page)
          if (onProgress(page.pageNumber, page.nPages)) {
            step // recurse asynchronously
          } else {
            cancel("You cancelled an import job")
          }
        }
        case ReadOneResult.Error(message) => cancel(message)
        case ReadOneResult.NoMorePages => Future.successful(ReadAllResult.Pages(pages.toVector))
      })
    }

    step
  }
}

object SplitPdfAndExtractTextReader {
  sealed trait ReadOneResult
  object ReadOneResult {
    /** Details about a page we've extracted.
      */
    case class Page(
      /** Page number, starting at 1. */
      pageNumber: Int,

      /** Total number of pages. */
      nPages: Int,

      /** If true, pdfocr may have generated some of this text via Tesseract. */
      isFromOcr: Boolean,

      /** The text contents of the page.
        *
        * These have already have Textify() called on them, by
        * SplitPdfAndExtractText.
        */
      text: String,

      /** If set, a file on the filesystem containing a PDF version of this page.
        */
      pdfPath: Option[Path],

      /** If set, a file on the filesystem containing a PNG thumbnail version of this page.
        */
      thumbnailPath: Option[Path]
    ) extends ReadOneResult

    /** The parser completed successfully. */
    object NoMorePages extends ReadOneResult

    case class Error(message: String) extends ReadOneResult
  }

  sealed trait ReadAllResult
  object ReadAllResult {
    /** Becomes one or several Documents. */
    case class Pages(pages: Vector[ReadOneResult.Page]) extends ReadAllResult {
      def cleanup: Future[Unit] = ???
      // TODO cleanup:
      //_ <- Future(blocking(pageInfos.flatMap(_.pdfPath).foreach(JFiles.delete _)))
      //_ <- Future(blocking(pageInfos.flatMap(_.thumbnailPath).foreach(JFiles.delete _)))
    }

    /** Becomes a DocumentProcessingError */
    case class Error(message: String) extends ReadAllResult
  }
}
