package models.export

import java.io.{ FileInputStream, OutputStream }

import org.overviewproject.util.TempFile

/** An export operation.
  *
  * An export is a potentially slow operation that writes a bunch of bytes to
  * an output stream.
  *
  * Implementing classes must provide an exportTo() implementation. That
  * operation may be slow.
  *
  * Callers can ignore most input/output details and call exportToInputStream,
  * which returns a FileInputStream. exportToInputStream() is slow, but reads
  * from its return value are as fast as any other disk reads.
  */
trait Export {
  /** Exports to an output stream.
    *
    * This method does <em>not</em> call outputStream.close.
    */
  def exportTo(outputStream: OutputStream) : Unit

  /** The Content-Type HTTP header, as a string.
    *
    * For instance, this could be "<tt>text/plain; charset="utf-8"</tt>"
    */
  def contentTypeHeader : String

  /** Exports to a file, and returns an InputStream for that file.
    *
    * The expected usage is from inside a controller:
    *
    *   val stream = export.exportToInputStream
    *   Ok(stream).withHeaders(...)
    *
    * The file will be deleted when the returned InputStream is closed. If you
    * forget to close it but lose a reference to it, the JVM will close it
    * while garbage collecting.
    *
    * We return a FileInputStream so callers can incorporate java.nio features.
    */
  def exportToInputStream : FileInputStream = {
    val tempFile = new TempFile
    exportTo(tempFile.outputStream)
    tempFile.outputStream.close
    tempFile.inputStream
  }

  /** Writes a UTF-8 byte-order marker to the output stream.
    *
    * Use this for export to programs that do not recognize UTF-8 always, such
    * as MS Excel.
    *
    * https://www.pivotaltracker.com/s/projects/928628/stories/62559464
    */
  protected def writeUtf8Bom(outputStream: OutputStream) : Unit = {
    outputStream.write(Array[Byte](0xef.toByte, 0xbb.toByte, 0xbf.toByte))
  }
}
