package com.overviewdocs.pdfocr

import com.google.common.io.ByteStreams
import java.io.{DataInputStream,InputStream}
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.{ExecutionContext,Future,blocking}

class SplitPdfAndExtractTextParser(val inputStream: InputStream) {
  import SplitPdfAndExtractTextParser.{State,Token}

  private val dataInputStream = new DataInputStream(inputStream)
  private var state: State = State.Head

  private def readBoolean(implicit ec: ExecutionContext): Future[Boolean] = {
    Future(blocking(dataInputStream.readBoolean))
  }

  private def readByte(implicit ec: ExecutionContext): Future[Byte] = {
    Future(blocking(dataInputStream.readByte))
  }

  private def readInt(implicit ec: ExecutionContext): Future[Int] = {
    Future(blocking(dataInputStream.readInt))
  }

  private def readString(implicit ec: ExecutionContext): Future[String] = {
    readInt.flatMap((nBytes: Int) => {
      Future(blocking {
        val bytes = Array.fill[Byte](nBytes)(0)
        dataInputStream.readFully(bytes)
        new String(bytes, UTF_8)
      })
    })
  }

  private def readHeader(implicit ec: ExecutionContext): Future[(Token,State)] = {
    readByte.flatMap(_ match {
      case 0x01 => readInt.map(size => {
        (Token.Header(size), State.ExpectNPages(size))
      })
      case 0x03 => readFooter
      case _ => invalidInput("Error parsing HEADER")
    })
  }

  private def invalidInput(message: String): Future[(Token,State)] = {
    Future.successful((
      Token.InvalidInput(message),
      State.Done
    ))
  }

  private def readPageAfterPdf(isOcr: Boolean, nRemaining: Int)(implicit ec: ExecutionContext): Future[(Token,State)] = {
    readString.map(text => {
      (
        Token.Page(isOcr, text),
        State.ExpectNPages(nRemaining - 1)
      )
    })
  }

  private def readPageAfterThumbnail(isOcr: Boolean, nRemaining: Int)(implicit ec: ExecutionContext): Future[(Token,State)] = {
    readInt.flatMap(_ match {
      case 0 => readPageAfterPdf(isOcr, nRemaining)
      case nPdfBytes: Int => {
        val subStream = ByteStreams.limit(inputStream, nPdfBytes)
        Future.successful((
          Token.PagePdf(nPdfBytes, subStream),
          State.GotPdfExpectNPagesIncludingThisOne(isOcr, nRemaining, subStream)
        ))
      }
    })
  }

  private def readFooter(implicit ec: ExecutionContext): Future[(Token,State)] = {
    readString.map(_ match {
      case "" => (Token.Success, State.Done)
      case message: String => (Token.PdfError(message), State.Done)
    })
  }

  private def readPage(nRemaining: Int)(implicit ec: ExecutionContext): Future[(Token,State)] = {
    readByte.flatMap((b: Byte) => (b, nRemaining) match {
      case (0x02, 0) => invalidInput("Got PAGE, expected FOOTER")
      case (0x02, _) => {
        readBoolean.flatMap((isOcr: Boolean) => {
          readInt.flatMap(_ match {
            case 0 => readPageAfterThumbnail(isOcr, nRemaining)
            case nThumbnailBytes: Int => {
              val subStream = ByteStreams.limit(inputStream, nThumbnailBytes)
              Future.successful((
                Token.PageThumbnail(nThumbnailBytes, subStream),
                State.GotThumbnailExpectNPagesIncludingThisOne(isOcr, nRemaining, subStream)
              ))
            }
          })
        })
      }
      case (0x03, _) => readFooter // whether or not there are pages remaining, this is success
      case _ => invalidInput("Error parsing PAGE or FOOTER")
    })
  }

  def next(implicit ec: ExecutionContext): Future[Token] = {
    val future: Future[(Token, State)] = state match {
      case State.Head => readHeader
      case State.ExpectNPages(nRemaining: Int) => readPage(nRemaining)
      case State.GotThumbnailExpectNPagesIncludingThisOne(isOcr: Boolean, nRemaining: Int, subStream: InputStream) => {
        Future(blocking(ByteStreams.exhaust(subStream))).flatMap(_ => {
          readPageAfterThumbnail(isOcr, nRemaining)
        })
      }
      case State.GotPdfExpectNPagesIncludingThisOne(isOcr: Boolean, nRemaining: Int, subStream: InputStream) => {
        Future(blocking(ByteStreams.exhaust(subStream))).flatMap(_ => {
          readPageAfterPdf(isOcr, nRemaining)
        })
      }
      case _ => ???
    }

    future
      .recover {
        case _: java.io.EOFException => (Token.InvalidInput("Unexpected end of input"), State.Done)
        case ex: java.io.IOException => (Token.InvalidInput("Exception: " + ex.getMessage), State.Done)
      }
      .map(t => {
        state = t._2
        t._1
      })
  }
}

object SplitPdfAndExtractTextParser {
  sealed trait Token
  sealed trait FinalToken
  object Token {
    /** First token in a stream, saying how many PAGES there ought to be. */
    case class Header(nPages: Int) extends Token

    /** A PNG blob describing a thumbnail for an as-yet unparsed Page.
      *
      * The caller is expected to empty the inputStream before continuing.
      */
    case class PageThumbnail(nBytes: Int, inputStream: InputStream) extends Token

    /** A PDF blob describing a an as-yet unparsed Page.
      *
      * The caller is expected to empty the inputStream before continuing.
      */
    case class PagePdf(nBytes: Int, inputStream: InputStream) extends Token

    /** A fully-processed Page. */
    case class Page(isOcr: Boolean, text: String) extends Token

    /** An error in the PDF. This will be the final token. */
    case class PdfError(message: String) extends Token with FinalToken

    /** The input stream is invalid. */
    case class InvalidInput(message: String) extends Token with FinalToken

    /** All input was parsed successfully. */
    case object Success extends Token with FinalToken
  }

  /** Where the parser is in the stream. */
  private sealed trait State
  private object State {
    /** Beginning of the stream. */
    case object Head extends State

    /** After a completely-handled HEADER or PAGE. */
    case class ExpectNPages(n: Int) extends State

    /** Just returned a PageThumbnail (InputStream).
      *
      * If subStream isn't exhausted, we'll exhaust it ourselves.
      */
    case class GotThumbnailExpectNPagesIncludingThisOne(isOcr: Boolean, n: Int, subStream: InputStream) extends State

    /** Just returned a PagePdf (InputStream).
      *
      * If subStream isn't exhausted, we'll exhaust it ourselves.
      */
    case class GotPdfExpectNPagesIncludingThisOne(isOcr: Boolean, n: Int, subStream: InputStream) extends State

    /** Terminal state. */
    case object Done extends State
  }
}
