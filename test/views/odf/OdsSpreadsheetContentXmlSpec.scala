package views.xml.odf

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class OdsSpreadsheetContentXmlSpec extends Specification {
  trait SpreadsheetScope extends Scope {
    val headers : Iterable[String] = Seq("header 1", "header 2", "header 3")
    val rows : Iterable[Iterable[Any]] = Seq(
      Seq("one", "two", "three"),
      Seq("four", "five", "six")
    )

    def spreadsheet = models.odf.OdsSpreadsheet(headers, rows)
    def xml = OdsSpreadsheetContentXml(spreadsheet)
    def text = xml.toString
  }

  "OdsSpreadsheetContentXml" should {
    "have an office:mimetype of application/vnd.oasis.opendocument.spreadsheet" in new SpreadsheetScope {
      text must contain("""office:mimetype="application/vnd.oasis.opendocument.spreadsheet"""")
    }

    "put headers in a <text:h>" in new SpreadsheetScope {
      text must contain("<text:h>header 1</text:h>")
    }

    "put contents in a <text:p>" in new SpreadsheetScope {
      text must contain("<text:p>five</text:p>")
    }

    "convert anything into a string" in new SpreadsheetScope {
      trait X extends Any
      trait Y extends Any
      override val rows = Seq(Seq(new X { override def toString = "foo" }), Seq(new Y { override def toString = "bar" }))
      text must contain("foo")
      text must contain("bar")
    }
  }
}
