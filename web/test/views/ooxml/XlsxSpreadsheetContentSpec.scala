package views.ooxml

import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.lang.StringBuilder
import org.specs2.specification.Scope
import scala.collection.immutable
import scala.concurrent.Future

import models.export.rows.Rows
import com.overviewdocs.util.AwaitMethod
import test.helpers.InAppSpecification // for materializer

class XlsxSpreadsheetContentSpec extends InAppSpecification with AwaitMethod {
  trait SpreadsheetScope extends Scope {
    val headers : Array[String] = Array("header 1", "header 2", "header 3")
    val rows : Source[Array[String], akka.NotUsed] = Source(immutable.Seq(
      Array("one", "two", "three"),
      Array("four", "five", "six")
    ))

    lazy val text: String = {
      val outputBuffer = new StringBuilder()
      def write(s: String) = { outputBuffer.append(s) }
      write(XlsxSpreadsheetContent.header)
      write(XlsxSpreadsheetContent.row(headers))
      await(rows.runFold(()) { case ((), arr: Array[String]) => write(XlsxSpreadsheetContent.row(arr)) })
      write(XlsxSpreadsheetContent.footer)
      outputBuffer.toString
    }
  }

  "XlsxWorksheetContentSpec" should {
    "have the correct XMLNS" in new SpreadsheetScope {
      text must contain("""xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"""")
    }

    "start with a <sheetData>" in new SpreadsheetScope {
      text must contain("<sheetData>")
    }

    "put headers in inlineStr cells" in new SpreadsheetScope {
      text must contain("""<c t="inlineStr"><is><t>header 1</t></is></c>""")
    }

    "put contents in a inlineStr cells" in new SpreadsheetScope {
      text must contain("""<c t="inlineStr"><is><t>five</t></is></c>""")
    }

    "not put an <is> for an empty string" in new SpreadsheetScope {
      override val rows = Source.single(Array(""))
      text must contain("""<c t="inlineStr"/>""")
    }

    "truncate cells to 32767 characters" in new SpreadsheetScope {
      // http://office.microsoft.com/en-ca/excel-help/excel-specifications-and-limits-HP005199291.aspx
      val xxx = "x" * 32768
      override val rows = Source.single(Array(xxx))
      text must contain(">" + xxx.substring(0, 32767) + "<")
      text must not(contain(xxx))
    }

    "respect XML entities while truncating" in new SpreadsheetScope {
      // https://www.pivotaltracker.com/story/show/77496514
      val xxx = "x" * 32763
      override val rows = Source.single(Array(xxx + "&"))
      text must contain(">" + xxx + "<")
    }

    "respect XML numeric entities while truncating" in new SpreadsheetScope {
      // https://www.pivotaltracker.com/story/show/77496514
      // &quot; actually becomes &#x34; with our XML implementation
      val xxx = "x" * 32763
      override val rows = Source.single(Array(xxx + "\""))
      text must contain(">" + xxx + "<")
    }

    "use spaces instead of invalid control characters" in new SpreadsheetScope {
      // We can't support them. Excel has an escape notation, but it doesn't, erm, work.
      override val rows = Source.single(Array("\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u0009\u000a\u000b\u000c\u000d\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"))
      text must contain("<t>         \u0009\n  \r                  </t>")
    }

    "escapes ST_Xstring-like escapes" in new SpreadsheetScope {
      // See http://www.robweir.com/blog/2008/03/ooxmls-out-of-control-characters.html
      override val rows = Source.single(Array("___x0004___"))
      text must contain("<t>___x005F_x0004___</t>")
    }
  }
}
