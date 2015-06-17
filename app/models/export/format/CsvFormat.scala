package models.export.format

import au.com.bytecode.opencsv.CSVWriter
import java.io.{BufferedWriter,OutputStream,OutputStreamWriter}
import play.api.libs.iteratee.Iteratee
import scala.concurrent.{Future,blocking}

import models.export.rows.Rows

object CsvFormat extends Format {
  override val contentType = """text/csv; charset="utf-8""""

  override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream): Future[Unit] = {
    for {
      csvWriter <- Future(blocking {
        writeUtf8Bom(outputStream)

        val writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))
        val csvWriter = new CSVWriter(writer)

        csvWriter.writeNext(rows.headers)
        csvWriter
      })
      _ <- rows.rows.run(Iteratee.foreach[Array[String]](row => blocking(csvWriter.writeNext(row))))
    } yield {
      blocking(csvWriter.close)
      ()
    }
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

  private def writeRow(row: Array[String], csvWriter: CSVWriter) : Unit = {
    csvWriter.writeNext(row)
  }
}
