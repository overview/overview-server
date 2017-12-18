package com.overviewdocs.pdfocr

import java.io.ByteArrayInputStream
import java.nio.file.{Files,Path}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{After,Scope}
import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer

import com.overviewdocs.util.AwaitMethod

class SplitPdfAndExtractTextReaderSpec extends Specification with Mockito {
  import scala.concurrent.ExecutionContext.Implicits.global
  import SplitPdfAndExtractTextParser.Token
  import SplitPdfAndExtractTextReader.{ReadOneResult,ReadAllResult}

  trait BaseScope extends Scope with AwaitMethod with After {
    val tempDir: Path = Files.createTempDirectory("SplitPdfAndExtractTextReaderSpec")
    val tokens: List[Token]
    def bytes(b: Byte*): Array[Byte] = b.toArray
    def stream(b: Byte*) = new ByteArrayInputStream(b.toArray)
    def fileBytes(f: Path): Array[Byte] = Files.readAllBytes(f)
    lazy val tokensIterator = tokens.iterator
    lazy val parser = mock[SplitPdfAndExtractTextParser]
    parser.next answers { _ => Future.successful(tokensIterator.next) }
    lazy val reader = new SplitPdfAndExtractTextReader(parser, tempDir)

    override def after = {
      import scala.compat.java8.FunctionConverters._
      Files.list(tempDir).forEach((Files.delete _).asJava)
      Files.deleteIfExists(tempDir)
    }
  }

  "read" should {
    "return a Page" in new BaseScope {
      override val tokens = List(Token.Header(3), Token.Page(false, "text"))
      await(reader.readOne) must beEqualTo(ReadOneResult.Page(
        pageNumber=1,
        nPages=3,
        isFromOcr=false,
        text="text",
        pdfPath=None,
        thumbnailPath=None
      ))
    }

    "return a second Page" in new BaseScope {
      override val tokens = List(Token.Header(3), Token.Page(false, "text"), Token.Page(true, "text 2"))
      await(reader.readOne)
      await(reader.readOne) must beEqualTo(ReadOneResult.Page(
        pageNumber=2,
        nPages=3,
        isFromOcr=true,
        text="text 2",
        pdfPath=None,
        thumbnailPath=None
      ))
    }

    "return an Error when first token is an PdfError" in new BaseScope {
      override val tokens = List(Token.PdfError("Blah"))
      await(reader.readOne) must beEqualTo(ReadOneResult.Error("Blah"))
    }

    "return an Error when first token is an InvalidInput" in new BaseScope {
      override val tokens = List(Token.InvalidInput("Blah"))
      await(reader.readOne) must beEqualTo(ReadOneResult.Error("Blah"))
    }

    "return an Error when first token is not a Header or Error" in new BaseScope {
      override val tokens = List(Token.Page(false, "text"))
      await(reader.readOne) must beEqualTo(ReadOneResult.Error("Error in parser"))
    }

    "return an Error when the Page is a PdfError" in new BaseScope {
      override val tokens = List(Token.Header(1), Token.PdfError("foo"))
      await(reader.readOne) must beEqualTo(ReadOneResult.Error("foo"))
    }

    "return an Error when the Page is a InvalidInput" in new BaseScope {
      override val tokens = List(Token.Header(1), Token.InvalidInput("foo"))
      await(reader.readOne) must beEqualTo(ReadOneResult.Error("foo"))
    }

    "return an Error when the Page is not a Page" in new BaseScope {
      override val tokens = List(Token.Header(2), Token.Header(1))
      await(reader.readOne) must beEqualTo(ReadOneResult.Error("Error in parser"))
    }

    "return NoMorePages when there are no more Pages" in new BaseScope {
      override val tokens = List(Token.Header(2), Token.Page(false, "text"), Token.Success)
      await(reader.readOne)
      await(reader.readOne) must beEqualTo(ReadOneResult.NoMorePages)
    }

    "read a Page with a Thumbnail" in new BaseScope {
      override val tokens = List(Token.Header(2), Token.PageThumbnail(1, stream(2)), Token.Page(false, "text"))
      await(reader.readOne) match {
        case page: ReadOneResult.Page => {
          page.text must beEqualTo("text")
          page.thumbnailPath must beSome
          page.thumbnailPath.get.getFileName.toString must beEqualTo("p1.png")
          fileBytes(page.thumbnailPath.get) must beEqualTo(bytes(2))
        }
        case _ => ???
      }
    }

    "read a Page _without_ a thumbnail after reading a page _with_" in new BaseScope {
      override val tokens = List(
        Token.Header(2),
        Token.PageThumbnail(1, stream(2)),
        Token.Page(false, "text"),
        Token.Page(false, "text 2")
      )
      await(reader.readOne)
      await(reader.readOne) match {
        case page: ReadOneResult.Page => page.thumbnailPath must beNone
        case _ => ???
      }
    }

    "read a Page with a Pdf" in new BaseScope {
      override val tokens = List(Token.Header(2), Token.PagePdf(1, stream(2)), Token.Page(false, "text"))
      await(reader.readOne) match {
        case page: ReadOneResult.Page => {
          page.text must beEqualTo("text")
          page.pdfPath must beSome
          page.pdfPath.get.getFileName.toString must beEqualTo("p1.pdf")
          fileBytes(page.pdfPath.get) must beEqualTo(bytes(2))
        }
        case _ => ???
      }
    }

    "read a Page _without_ a Pdf after reading a page _with_" in new BaseScope {
      override val tokens = List(
        Token.Header(2),
        Token.PagePdf(1, stream(2)),
        Token.Page(false, "text"),
        Token.Page(false, "text 2")
      )
      await(reader.readOne)
      await(reader.readOne) match {
        case page: ReadOneResult.Page => page.pdfPath must beNone
        case _ => ???
      }
    }

    "delete thumbnail and pdf if we get an error before the page" in new BaseScope {
      override val tokens = List(
        Token.Header(1),
        Token.PageThumbnail(1, stream(2)),
        Token.PagePdf(1, stream(3)),
        Token.InvalidInput("EOF")
      )
      await(reader.readOne)
      Files.exists(tempDir.resolve("p1.pdf")) must beFalse
      Files.exists(tempDir.resolve("p1.png")) must beFalse
    }
  }

  "readAll" should {
    trait ReadAllScope extends BaseScope {
      def onProgressReturn = true
      var onProgressCalls = ArrayBuffer.empty[(Int,Int)]
      def onProgress(i1: Int, i2: Int): Boolean = {
        onProgressCalls.+=((i1, i2))
        onProgressReturn
      }
    }

    "return Pages" in new ReadAllScope {
      override val tokens = List(
        Token.Header(2),
        Token.Page(false, "1"),
        Token.Page(false, "2"),
        Token.Success
      )
      await(reader.readAll(onProgress)) must beEqualTo(ReadAllResult.Pages(Vector(
        ReadOneResult.Page(1, 2, false, "1", None, None),
        ReadOneResult.Page(2, 2, false, "2", None, None),
      ), reader.tempDir))
    }

    "call onProgress after each page" in new ReadAllScope {
      override val tokens = List(
        Token.Header(2),
        Token.Page(false, "1"),
        Token.Page(false, "2"),
        Token.Success
      )
      await(reader.readAll(onProgress))
      onProgressCalls must beEqualTo(ArrayBuffer((1,2),(2,2)))
    }

    "cancel if onProgress returns false" in new ReadAllScope {
      override val tokens = List(
        Token.Header(3),
        Token.Page(false, "1"),
        Token.Page(false, "2"),
        Token.Page(false, "3"),
        Token.Success
      )
      override def onProgressReturn = onProgressCalls.size < 2
      await(reader.readAll(onProgress)) must beEqualTo(ReadAllResult.Error("You cancelled an import job", reader.tempDir))
    }

    "delete files when cancelling" in new ReadAllScope {
      override val tokens = List(
        Token.Header(3),
        Token.PageThumbnail(1, stream(2)),
        Token.PagePdf(1, stream(3)),
        Token.Page(false, "1"),
        Token.Page(false, "2"),
        Token.Page(false, "3"),
        Token.Success
      )
      override def onProgressReturn = onProgressCalls.size < 2
      await(reader.readAll(onProgress))
      Files.exists(tempDir.resolve("p1.pdf")) must beFalse
      Files.exists(tempDir.resolve("p1.png")) must beFalse
    }

    "delete files on error" in new ReadAllScope {
      override val tokens = List(
        Token.Header(3),
        Token.PageThumbnail(1, stream(2)),
        Token.PagePdf(1, stream(3)),
        Token.Page(false, "1"),
        Token.PdfError("error")
      )
      override def onProgressReturn = onProgressCalls.size < 2
      await(reader.readAll(onProgress))
      Files.exists(tempDir.resolve("p1.pdf")) must beFalse
      Files.exists(tempDir.resolve("p1.png")) must beFalse
    }

    "delete files on cleanup" in new ReadAllScope {
      override val tokens = List(
        Token.Header(2),
        Token.PageThumbnail(1, stream(2)),
        Token.PagePdf(1, stream(3)),
        Token.Page(false, "1"),
        Token.PageThumbnail(1, stream(2)),
        Token.PagePdf(1, stream(3)),
        Token.Page(false, "2"),
        Token.Success
      )
      val result = await(reader.readAll(onProgress))
      Files.exists(tempDir.resolve("p1.pdf")) must beTrue
      Files.exists(tempDir.resolve("p1.png")) must beTrue
      Files.exists(tempDir.resolve("p1.pdf")) must beTrue
      Files.exists(tempDir.resolve("p1.png")) must beTrue
      await(result.cleanup)
      Files.exists(tempDir.resolve("p1.pdf")) must beFalse
      Files.exists(tempDir.resolve("p1.png")) must beFalse
      Files.exists(tempDir.resolve("p1.pdf")) must beFalse
      Files.exists(tempDir.resolve("p1.png")) must beFalse
    }
  }
}
