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
      text must contain("""xmlns="http://purl.oclc.org/ooxml/spreadsheetml/main"""")
    }

    "start with a <sheetData>" in new SpreadsheetScope {
      text must contain("<sheetData>")
    }

    "put contents in a inlineStr cells" in new SpreadsheetScope {
      text must contain("""<c t="inlineStr"><is><t>five</t></is></c>""")
    }

    "not put an <is> for an empty string" in new SpreadsheetScope {
      override val vRows = Seq(Seq(""))
      text must contain("""<c t="inlineStr"/>""")
    }

    "truncate cells to 32767 characters" in new SpreadsheetScope {
      // http://office.microsoft.com/en-ca/excel-help/excel-specifications-and-limits-HP005199291.aspx
      override val vRows = Seq(Seq("x" * 32768))
      text must contain("x" * 32767)
      text must not(contain("x" * 32768))
    }

    "use ST_Xstring escaping for C0 control characters" in new SpreadsheetScope {
      // See http://www.robweir.com/blog/2008/03/ooxmls-out-of-control-characters.html
      // TODO: ban non-text characters?
      override val vRows = Seq(Seq("\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u0009\u000a\u000b\u000c\u000d\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"))
      text must contain("<t>_x0000__x0001__x0002__x0003__x0004__x0005__x0006__x0007__x0008_\u0009\n_x000B__x000C_\r_x000E__x000F__x0010__x0011__x0012__x0013__x0014__x0015__x0016__x0017__x0018__x0019__x001A__x001B__x001C__x001D__x001E__x001F_</t>")
    }

    "escapes ST_Xstring-like escapes" in new SpreadsheetScope {
      override val vRows = Seq(Seq("___x0004___"))
      text must contain("<t>___x005F_x0004___</t>")
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
