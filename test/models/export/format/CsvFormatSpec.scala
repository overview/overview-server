package models.export.format

import com.opencsv.CSVReader
import java.io.{ByteArrayOutputStream,StringReader}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}

import models.export.rows.Rows

class CsvFormatSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val headers : Array[String] = Array("header 1", "header 2", "header 3")
    val rows : Enumerator[Array[String]] = Enumerator(
      Array("one", "two", "three"),
      Array("four", "five", "six")
    )

    lazy val bytes = {
      val outputStream = new ByteArrayOutputStream
      await(CsvFormat.writeContentsToOutputStream(Rows(headers, rows), outputStream))
      outputStream.toByteArray
    }

    lazy val parsedCsv : Seq[Array[String]] = {
      import scala.collection.JavaConverters.asScalaBufferConverter
      // Drop the UTF-8 BOM when reading, so we can do string comparisons
      val csv = new CSVReader(new StringReader(new String(bytes.drop(3), "utf-8")))
      val rowsList = csv.readAll
      asScalaBufferConverter(rowsList).asScala
    }
  }

  trait StringScope extends BaseScope {
    override val headers = Array("col1", "col2", "col3")
    override val rows : Enumerator[Array[String]] = Enumerator(
      Array("val1", "val2", "val3"),
      Array("val4", "val5", "val6")
    )
  }

  "CsvFormat" should {
    "have the correct content-type" in {
      CsvFormat.contentType must beEqualTo("""text/csv; charset="utf-8"""")
    }

    "export a UTF-8 byte-order marker, to help MS Excel on Windows" in new StringScope {
      bytes.slice(0, 3) must beEqualTo(Array(0xef, 0xbb, 0xbf).map(_.toByte))
    }

    "export headers" in new StringScope {
      parsedCsv(0) must beEqualTo(Array("col1", "col2", "col3"))
    }

    "export values" in new StringScope {
      parsedCsv(1) must beEqualTo(Array("val1", "val2", "val3"))
      parsedCsv(2) must beEqualTo(Array("val4", "val5", "val6"))
      parsedCsv.length must beEqualTo(3)
    }
  }
}
