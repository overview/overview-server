package models.export.format

import au.com.bytecode.opencsv.CSVWriter
import java.io.{BufferedWriter,OutputStream,OutputStreamWriter}

import models.export.rows.Rows

object CsvFormat extends Format {
  override val contentType = """text/csv; charset="utf-8""""

  override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream) = {
    writeUtf8Bom(outputStream)

    val writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))
    val csvWriter = new CSVWriter(writer)

    writeHeaders(rows.headers, csvWriter)
    rows.rows.foreach(writeRow(_, csvWriter))

    csvWriter.close
  }

  /** Writes a UTF-8 byte-order marker to the output stream.
    *
    * Use this for export to programs that do not recognize UTF-8 always, such
    * as MS Excel.
    *
    * https://www.pivotaltracker.com/s/projects/928628/stories/62559464
    */
  private def writeUtf8Bom(outputStream: OutputStream) : Unit = {
    outputStream.write(Array[Byte](0xef.toByte, 0xbb.toByte, 0xbf.toByte))
  }

  private def writeHeaders(headers: Iterable[String], csvWriter: CSVWriter) : Unit = {
    csvWriter.writeNext(headers.toArray)
  }

  private def writeRow(row: Iterable[Any], csvWriter: CSVWriter) : Unit = {
    val strings : Array[String] = row.map(_.toString).toArray
    csvWriter.writeNext(strings)
  }
}
