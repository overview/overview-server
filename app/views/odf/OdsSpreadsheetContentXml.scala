package views.odf

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import scala.concurrent.Future

import models.export.rows.Rows

object OdsSpreadsheetContentXml {
  def apply(rows: Rows, write: String => Future[Unit]): Future[Unit] = {
    for {
      _ <- write(header(rows.headers))
      _ <- rows.rows.run(Iteratee.foreach[Array[String]](values => write(row(values))))
      _ <- write(footer)
    } yield ()
  }

  private def row(values: Array[String]): String = {
    <table:table-row>
      {for (value <- values) yield <table:table-cell><text:p>{value}</text:p></table:table-cell>}
    </table:table-row>.toString
  }

  private def header(values: Array[String]): String = """<?xml version="1.0" encoding="utf-8"?>
<office:document-content
  xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
  office:mimetype="application/vnd.oasis.opendocument.spreadsheet"
  office:version="1.0"
  >
  <office:body>
    <office:spreadsheet>
      <table:table>
  """ /* not-Scala-XML, because we don't close these tags */ +
    <table:table-header-rows>
      <table:table-row>
        {for (value <- values) yield <table:table-cell><text:p>{value}</text:p></table:table-cell>}
      </table:table-row>
    </table:table-header-rows>.toString /* Scala-XML, because it escapes things nicely */

  private val footer: String = """
      </table:table>
    </office:spreadsheet>
  </office:body>
</office:document-content>"""
}
