package models.export.format

import java.io.{FileInputStream,OutputStream}

import org.overviewproject.util.TempFile
import models.export.rows.Rows

trait Format {
  /** The Content-Type header for this file. */
  val contentType: String

  /** Writes the headers and rows to the output stream. */
  def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream) : Unit

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
  def getContentsAsInputStream(rows: Rows) : FileInputStream = {
    val tempFile = new TempFile
    writeContentsToOutputStream(rows, tempFile.outputStream)
    tempFile.outputStream.close
    tempFile.inputStream
  }
}
