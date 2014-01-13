package models.export

import java.io.FileInputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import models.export.rows.Rows
import models.export.format.Format
import org.overviewproject.util.TempFile

class ExportSpec extends Specification with Mockito {
  def createFileInputStreamWithBytes(bytes: Array[Byte]) = {
    val tempFile = new TempFile()
    tempFile.outputStream.write(bytes)
    tempFile.outputStream.close()
    tempFile.inputStream
  }

  trait BaseScope extends Scope {
    val rows = mock[Rows]
    val format = mock[Format]

    format.contentType returns "application/foobar"
    format.getContentsAsInputStream(rows) returns createFileInputStreamWithBytes("foobar".getBytes("utf-8"))

    val export = new Export(rows, format)
  }

  "Export" should {
    "have the correct contentType" in new BaseScope {
      export.contentType must beEqualTo("application/foobar")
    }

    "return a FileInputStream" in new BaseScope {
      val is = export.asFileInputStream
      is must beAnInstanceOf[FileInputStream]
      val bytes = new Array[Byte](6)
      is.read(bytes)
      bytes must beEqualTo("foobar".getBytes("utf-8"))
    }
  }
}
