package models.export.format

import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

import models.export.rows.Rows

object CsvFormat extends Format {
  private val Utf8 = StandardCharsets.UTF_8
  private val CRLF = "\r\n" // We use String, because String concatenation uses StringBuilder and Array concatenation doesn't
  private val NeedsEscaping: Pattern = Pattern.compile("""[",\r\n]""")
  private val Utf8Bom = Array[Byte](0xef.toByte, 0xbb.toByte, 0xbf.toByte)
  private implicit val executionContext = play.api.libs.concurrent.Execution.defaultContext

  override val contentType = """text/csv; charset="utf-8""""

  /** Quotes a value for CSV output. */
  private def quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

  /** Prepares a cell of CSV output from the given value.
    *
    * In other words: this quotes a cell for CSV export.
    */
  private def cell(value: String): String = {
    if (NeedsEscaping.matcher(value).find) {
      quote(value)
    } else {
      value
    }
  }

  /** Prepares a row of CSV output from the given Array of values. */
  private def row(values: Array[String]): ByteString = {
    ByteString((values.map(cell).mkString(",") + CRLF).getBytes(Utf8))
  }

  override def byteSource(rows: Rows) = {
    Source.single(ByteString(Utf8Bom))
      .concat(Source.single(row(rows.headers)))
      .concat(rows.rows.map(arr => row(arr)))
  }
}
