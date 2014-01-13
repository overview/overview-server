package models.export.format

import java.io.{ByteArrayOutputStream,StringReader}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import models.export.rows.Rows

class OdsFormatSpec extends Specification {
  trait BaseScope extends Scope {
    val vHeaders : Iterable[String]
    val vRows: Iterable[Iterable[Any]]

    def rows = new Rows {
      override def headers = vHeaders
      override def rows = vRows
    }

    lazy val exportBytes : Array[Byte] = {
      val stream = new ByteArrayOutputStream
      OdsFormat.writeContentsToOutputStream(rows, stream)
      stream.toByteArray
    }
  }

  trait StringScope extends BaseScope {
    override val vHeaders = Seq("col1", "col2", "col3")
    override val vRows : Iterable[Iterable[Any]] = Seq(Seq("val1", "val2", "val3"), Seq("val4", "val5", "val6"))
  }

  "OdsFormat" should {
    "have the correct content-type" in new StringScope {
      OdsFormat.contentType must beEqualTo("""application/vnd.oasis.opendocument.spreadsheet""")
    }

    "have the correct magic number" in new StringScope {
      // Check for "PK"
      exportBytes(0) must beEqualTo(0x50.toByte)
      exportBytes(1) must beEqualTo(0x4b.toByte)
    }
  }
}
