package com.overviewdocs.pdfocr

import akka.util.ByteString
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

  private def readHeader(implicit ec: ExecutionContext): Future[(Option[Token],State)] = {
    readByte.flatMap(_ match {
      case 0x01 => readInt.map(size => {
        (Some(Token.Header(size)), State.ExpectNPages(size))
      })
      case 0x03 => readFooter
      case _ => invalidInput("Error parsing HEADER")
    })
  }

  private def invalidInput(message: String): Future[(Option[Token],State)] = {
    Future.successful((
      Some(Token.InvalidInput(message)),
      State.Done
    ))
  }

  private def readPageHeader(nRemaining: Int)(implicit ec: ExecutionContext): Future[(Option[Token],State)] = {
    readByte.flatMap((b: Byte) => (b, nRemaining) match {
      case (0x02, 0) => invalidInput("Got PAGE, expected FOOTER")
      case (0x02, _) => {
        readBoolean.flatMap((isOcr: Boolean) => {
          Future.successful((
            Some(Token.PageHeader(isOcr)),
            State.GotPageHeaderExpectNPagesIncludingThisOne(nRemaining)
          ))
        })
      }
      case (0x03, _) => readFooter // whether or not there are pages remaining, this is success
      case _ => invalidInput("Error parsing PAGE or FOOTER")
    })
  }

  private def readPageAfterHeader(nRemaining: Int)(implicit ec: ExecutionContext): Future[(Option[Token],State)] = {
    readInt.flatMap(_ match {
      case 0 => readPageAfterThumbnail(nRemaining)
      case nThumbnailBytes: Int => {
        // ICK ICK ICK. We have to read it all now, because the caller may call
        // .next anytime in the future and we'll want it to read the subsequent
        // byte.
        //
        // This could make us run out of RAM. We should change the binary to
        // upload to us via HTTP instead, so we can stream nicely.
        val buf = new Array[Byte](nThumbnailBytes)
        blocking(ByteStreams.readFully(inputStream, buf))
        Future.successful((
          Some(Token.PageThumbnail(ByteString(buf))),
          State.GotThumbnailExpectNPagesIncludingThisOne(nRemaining)
        ))
      }
    })
  }

  private def readPageAfterPdf(nRemaining: Int)(implicit ec: ExecutionContext): Future[(Option[Token],State)] = {
    readInt.flatMap { nTextBytes: Int =>
      // ICK ICK ICK. We have to read it all now, because the caller may call
      // .next anytime in the future and we'll want it to read the subsequent
      // byte.
      //
      // This could make us run out of RAM. We should change the binary to
      // upload to us via HTTP instead, so we can stream nicely.
      val buf = new Array[Byte](nTextBytes)
      blocking(ByteStreams.readFully(inputStream, buf))
      Future.successful((
        Some(Token.PageText(ByteString(buf))),
        State.GotTextExpectNPagesIncludingThisOne(nRemaining)
      ))
    }
  }

  private def readPageAfterThumbnail(nRemaining: Int)(implicit ec: ExecutionContext): Future[(Option[Token],State)] = {
    readInt.flatMap(_ match {
      case 0 => readPageAfterPdf(nRemaining)
      case nPdfBytes: Int => {
        // ICK ICK ICK. We have to read it all now, because the caller may call
        // .next anytime in the future and we'll want it to read the subsequent
        // byte.
        //
        // This could make us run out of RAM. We should change the binary to
        // upload to us via HTTP instead, so we can stream nicely.
        val buf = new Array[Byte](nPdfBytes)
        blocking(ByteStreams.readFully(inputStream, buf))
        Future.successful((
          Some(Token.PagePdf(ByteString(buf))),
          State.GotPdfExpectNPagesIncludingThisOne(nRemaining)
        ))
      }
    })
  }

  private def readFooter(implicit ec: ExecutionContext): Future[(Option[Token],State)] = {
    readString.map(_ match {
      case "" => (Some(Token.Success), State.Done)
      case message: String => (Some(Token.PdfError(message)), State.Done)
    })
  }

  def next(implicit ec: ExecutionContext): Future[Option[Token]] = {
    val future: Future[(Option[Token], State)] = state match {
      case State.Head => readHeader
      case State.ExpectNPages(nRemaining) => readPageHeader(nRemaining)
      case State.GotPageHeaderExpectNPagesIncludingThisOne(nRemaining) => readPageAfterHeader(nRemaining)
      case State.GotThumbnailExpectNPagesIncludingThisOne(nRemaining: Int) => readPageAfterThumbnail(nRemaining)
      case State.GotPdfExpectNPagesIncludingThisOne(nRemaining: Int) => readPageAfterPdf(nRemaining)
      case State.GotTextExpectNPagesIncludingThisOne(nRemaining: Int) => readPageHeader(nRemaining - 1)
      case State.Done => Future.successful((None, State.Done))
    }

    future
      .recover {
        case _: java.io.EOFException => (Some(Token.InvalidInput("Unexpected end of input")), State.Done)
        case ex: java.io.IOException => (Some(Token.InvalidInput("Exception: " + ex.getMessage)), State.Done)
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

    /** Information about the Page. */
    case class PageHeader(isOcr: Boolean) extends Token

    /** A PNG blob describing a thumbnail for an as-yet unparsed Page.
      *
      * The caller is expected to empty the inputStream before continuing.
      */
    case class PageThumbnail(byteString: ByteString) extends Token

    /** A PDF blob describing a an as-yet unparsed Page.
      *
      * The caller is expected to empty the inputStream before continuing.
      */
    case class PagePdf(byteString: ByteString) extends Token

    /** A fully-processed Page. */
    case class PageText(byteString: ByteString) extends Token

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

    case class ExpectNPages(n: Int) extends State

    /** Just returned a PageHeader. */
    case class GotPageHeaderExpectNPagesIncludingThisOne(n: Int) extends State

    /** Just returned a PageThumbnail (InputStream). */
    case class GotThumbnailExpectNPagesIncludingThisOne(n: Int) extends State

    /** Just returned a PagePdf (InputStream). */
    case class GotPdfExpectNPagesIncludingThisOne(n: Int) extends State

    /** Just returned a PageText (InputStream). */
    case class GotTextExpectNPagesIncludingThisOne(n: Int) extends State

    /** Terminal state. */
    case object Done extends State
  }
}
