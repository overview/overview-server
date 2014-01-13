package models.export.format

import au.com.bytecode.opencsv.CSVReader
import java.io.{ByteArrayOutputStream,StringReader}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import models.export.rows.Rows

class CsvFormatSpec extends Specification {
  trait BaseScope extends Scope {
    val vHeaders : Iterable[String]
    val vRows: Iterable[Iterable[Any]]

    def rows = new Rows {
      override def headers = vHeaders
      override def rows = vRows
    }

    lazy val exportBytes : Array[Byte] = {
      val stream = new ByteArrayOutputStream
      CsvFormat.writeContentsToOutputStream(rows, stream)
      stream.toByteArray
    }

    lazy val parsedCsv : Seq[Array[String]] = {
      import scala.collection.JavaConverters.asScalaBufferConverter
      // Drop the UTF-8 BOM when reading, so we can do string comparisons
      val csv = new CSVReader(new StringReader(new String(exportBytes.drop(3), "utf-8")))
      val rowsList = csv.readAll
      asScalaBufferConverter(rowsList).asScala
    }
  }

  trait StringScope extends BaseScope {
    override val vHeaders = Seq("col1", "col2", "col3")
    override val vRows : Iterable[Iterable[Any]] = Seq(Seq("val1", "val2", "val3"), Seq("val4", "val5", "val6"))
  }

  "CsvFormat" should {
    "have the correct content-type" in {
      CsvFormat.contentType must beEqualTo("""text/csv; charset="utf-8"""")
    }

    "export a UTF-8 byte-order marker, to help MS Excel on Windows" in new StringScope {
      exportBytes(0) must beEqualTo(0xef.toByte)
      exportBytes(1) must beEqualTo(0xbb.toByte)
      exportBytes(2) must beEqualTo(0xbf.toByte)
    }

    "export headers" in new StringScope {
      parsedCsv(0) must beEqualTo(Array("col1", "col2", "col3"))
    }

    "export values" in new StringScope {
      parsedCsv(1) must beEqualTo(Array("val1", "val2", "val3"))
      parsedCsv(2) must beEqualTo(Array("val4", "val5", "val6"))
      parsedCsv.length must beEqualTo(3)
    }

    "export integers as strings" in new StringScope {
      override val vRows = Seq(Seq("val1", "val2", 3))
      parsedCsv(1)(2) must beEqualTo("3")
    }
  }
}
