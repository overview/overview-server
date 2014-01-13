package views.odf

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.{ByteArrayInputStream,ByteArrayOutputStream}
import java.util.zip.{ZipEntry,ZipInputStream}

class OdfFileSpec extends Specification {
  trait SpreadsheetScope extends Scope {
    val headers : Iterable[String] = Seq("header 1", "header 2", "header 3")
    val rows : Iterable[Iterable[Any]] = Seq(
      Seq("one", "two", "three"),
      Seq("four", "five", "six")
    )

    def spreadsheet = models.odf.OdsSpreadsheet(headers, rows)
    def manifest = models.odf.OdfManifest(Seq(spreadsheet))
    def bytes = {
      val outputStream = new ByteArrayOutputStream()
      OdfFile(manifest).writeTo(outputStream)
      outputStream.toByteArray()
    }
    lazy val zipStream = {
      val inputStream = new ByteArrayInputStream(bytes)
      new ZipInputStream(inputStream)
    }
    def currentEntryContents(zipStream: ZipInputStream, len: Int) : Array[Byte] = {
      val bytes = new Array[Byte](len)
      zipStream.read(bytes, 0, len)
      bytes
    }
  }

  "OdfFile" should {
    "start with a mimetype file of the proper type" in new SpreadsheetScope {
      val goodMimeType = "application/vnd.oasis.opendocument.spreadsheet"
      val entry1 = Option(zipStream.getNextEntry())
      entry1 must beSome[ZipEntry].which(_.getName() == "mimetype")
      val contents = currentEntryContents(zipStream, goodMimeType.getBytes().length)
      new String(contents) must beEqualTo(goodMimeType)
    }

    "include the manifest" in new SpreadsheetScope {
      zipStream.getNextEntry() // skip mimetype
      val entry2 = Option(zipStream.getNextEntry())
      entry2 must beSome[ZipEntry].which(_.getName() == "META-INF/manifest.xml")
      val contents = currentEntryContents(zipStream, 5)
      new String(contents) must beEqualTo("<?xml")
    }

    "include the spreadsheet contents" in new SpreadsheetScope {
      zipStream.getNextEntry() // skip mimetype
      zipStream.getNextEntry() // skip manifest
      val entry3 = Option(zipStream.getNextEntry())
      entry3 must beSome[ZipEntry].which(_.getName() == "content.xml")
      val contents = currentEntryContents(zipStream, 5)
      new String(contents) must beEqualTo("<?xml")
    }
  }
}
