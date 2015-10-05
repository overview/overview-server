package views.ooxml

import play.api.libs.iteratee.Iteratee
import scala.concurrent.{ExecutionContext,Future}

import models.export.rows.Rows

object XlsxSpreadsheetContent {
  def apply(rows: Rows, write: (String => Future[Unit]))(implicit executionContext: ExecutionContext): Future[Unit] = {
    for {
      _ <- write(header)
      _ <- write(row(rows.headers))
      _ <- rows.rows.run(Iteratee.foreach[Array[String]](values => write(row(values))))
      _ <- write(footer)
    } yield ()
  }

  val header: String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet
    xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">

  <sheetData>"""

  val footer: String = """
  </sheetData>
</worksheet>"""

  def row(values: Array[String]): String = {
    s"""<row>${values.map(cell).mkString("")}</row>"""
  }

  private def cell(value: String): String = {
    if (value.isEmpty) {
      """<c t="inlineStr"/>"""
    } else {
      s"""<c t="inlineStr"><is><t>${escape(value)}</t></is></c>"""
    }
  }

  /** "XML"-escapes the given text
    *
    * Excel's "XML" is invalid XML. DO NOT use normal XML-escaped strings in
    * Excel spreadsheets. Escape them with this method instead.
    *
    * Search for ST_XstringEscape for an explanation of Microsoft's decisions.
    */
  private def escape(text: String): String = {
    // Excel translates "_x0001_" to a control character. Then it crashes when
    // it tries to _open_ a file with a control character. So let's avoid its
    // stupid escaping mechanism.
    // "_x0003_" -> "_x005F_x0003_" -- escape the escape
    val xstring = """_x[0-9a-fA-F]{4}_""".r.replaceAllIn(text, (m => s"_x005F${m.matched}"))

    // See http://www.w3.org/TR/xml/#charsets
    // Why not just use org.owasp.encoder.Encode.forXml(xstring)? Because
    // https://code.google.com/p/owasp-java-encoder/issues/detail?id=4
    val writer = new java.io.StringWriter
    org.owasp.encoder.Encode.forXml(writer, xstring)
    val valid = writer.toString

    // Truncate. Excel only allows 32767 characters per cell.
    // http://office.microsoft.com/en-ca/excel-help/excel-specifications-and-limits-HP005199291.aspx
    val truncated = valid.take(32767)

    // Avoid splitting XML entities. Try to keep the regex fast; there are only
    // five XML entities, after all: &lt;, &gt;, &amp;, &quot;, &apos; ... and
    // numeric entities.
    // Longest entity is "&#x10fffd;" or "&#1114109;"
    val truncatedXmlEntityAtEnd = """&[#ltgampquosx0-9]{0,8}$""".r
    truncatedXmlEntityAtEnd.replaceFirstIn(truncated, "")
  }
}
