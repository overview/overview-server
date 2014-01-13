package views.xml.odf

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class OdfManifestXmlSpec extends Specification {
  trait SpreadsheetScope extends Scope {
    val headers : Iterable[String] = Seq("header 1", "header 2", "header 3")
    val rows : Iterable[Iterable[Any]] = Seq(
      Seq("one", "two", "three"),
      Seq("four", "five", "six")
    )

    def spreadsheet = models.odf.OdsSpreadsheet(headers, rows)
    def manifest = models.odf.OdfManifest(Seq(spreadsheet))
    def xml = OdfManifestXml(manifest)
    def text = xml.toString
  }

  "OdfManifestXml" should {
    "have a manifest:file-entry for a spreadsheet" in new SpreadsheetScope {
      text must contain("""manifest:media-type="text/xml"""")
      text must contain("""manifest:full-path="content.xml"""")
    }

    "describe the entire zipfile" in new SpreadsheetScope {
      text must contain("""manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"""")
      text must contain("""manifest:full-path="/"""")
    }
  }
}
