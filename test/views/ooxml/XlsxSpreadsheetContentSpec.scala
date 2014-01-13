package views.xml.ooxml

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class XlsxSpreadsheetContentSpec extends Specification {
  trait SpreadsheetScope extends Scope {
    val vHeaders : Iterable[String] = Seq("header 1", "header 2", "header 3")
    val vRows : Iterable[Iterable[Any]] = Seq(
      Seq("one", "two", "three"),
      Seq("four", "five", "six")
    )

    def spreadsheet = new models.export.rows.Rows {
      override def headers = vHeaders
      override def rows = vRows
    }

    def xml = XlsxSpreadsheetContent(spreadsheet)
    def text = xml.toString
  }

  "XlsxWorksheetContentSpec" should {
    "have the correct XMLNS" in new SpreadsheetScope {
      text must contain("""xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"""")
    }

    "start with a <sheetData>" in new SpreadsheetScope {
      text must contain("<sheetData>")
    }

    "put contents in a <text:p>" in new SpreadsheetScope {
      text must contain("""<c t="s"><v>five</v></c>""")
    }

    "add a row number" in new SpreadsheetScope {
      text must contain("""<row r="3">""")
      text must not contain("""<row r="4">""")
    }

    "convert anything into a string" in new SpreadsheetScope {
      trait X extends Any
      trait Y extends Any
      override val vRows = Seq(Seq(new X { override def toString = "foo" }), Seq(new Y { override def toString = "bar" }))
      text must contain("foo")
      text must contain("bar")
    }
  }
}
