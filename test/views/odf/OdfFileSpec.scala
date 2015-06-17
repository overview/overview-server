package views.odf

import java.io.{ByteArrayInputStream,ByteArrayOutputStream}
import java.util.zip.{ZipEntry,ZipInputStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}

import models.export.rows.Rows

class OdfFileSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait SpreadsheetScope extends Scope {
    val rows = Rows(
      Array("header 1", "header 2", "header 3"),
      Enumerator(
        Array("one", "two", "three"),
        Array("four", "five", "six")
      )
    )

    lazy val zipStream: ZipInputStream = {
      val outputStream = new ByteArrayOutputStream()
      await(for {
        _ <- OdfFile(rows).writeTo(outputStream)
      } yield {
        outputStream.close
        val bytes = outputStream.toByteArray
        val inputStream = new ByteArrayInputStream(bytes)
        new ZipInputStream(inputStream)
      })
    }

    def currentEntryContents(len: Int) : Array[Byte] = {
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
      val contents = currentEntryContents(goodMimeType.getBytes().length)
      new String(contents) must beEqualTo(goodMimeType)
    }

    "include the manifest" in new SpreadsheetScope {
      zipStream.getNextEntry() // skip mimetype
      val entry2 = Option(zipStream.getNextEntry())
      entry2 must beSome[ZipEntry].which(_.getName() == "META-INF/manifest.xml")
      val contents = currentEntryContents(5)
      new String(contents) must beEqualTo("<?xml")
    }

    "include the spreadsheet contents" in new SpreadsheetScope {
      zipStream.getNextEntry() // skip mimetype
      zipStream.getNextEntry() // skip manifest
      val entry3 = Option(zipStream.getNextEntry())
      entry3 must beSome[ZipEntry].which(_.getName() == "content.xml")
      val contents = currentEntryContents(5)
      new String(contents) must beEqualTo("<?xml")
    }
  }
}
