package models.export.format

import java.io.{FileInputStream,OutputStream}
import scala.concurrent.{Future,blocking}

import org.overviewproject.util.TempFile
import models.export.rows.Rows

trait Format {
  protected implicit val executionContext = play.api.libs.concurrent.Execution.defaultContext

  /** The Content-Type header for this file. */
  val contentType: String

  /** Writes the headers and rows to the output stream. */
  protected def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream): Future[Unit]

  /** Exports to a file, and returns a FileInputStream for that file.
    *
    * The expected usage is from inside a controller:
    *
    *   val stream = export.getContentsAsInputStream(headers, rows)
    *   Ok(stream).withHeaders(...)
    *
    * The file will be deleted when the returned InputStream is closed. If you
    * forget to close it but lose a reference to it, the JVM will close it
    * while garbage collecting.
    *
    * We return a FileInputStream so callers can incorporate java.nio features.
    *
    * @see writeContentsToOutputStream
    * @see org.overviewproject.util.TempFile
    */
  def getContentsAsInputStream(rows: Rows): Future[FileInputStream] = {
    val tempFile = blocking(new TempFile)

    for {
      _ <- writeContentsToOutputStream(rows, tempFile.outputStream)
    } yield {
      blocking(tempFile.outputStream.close)
      tempFile.inputStream
    }
  }
}
