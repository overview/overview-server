/*
 * UploadReader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.{BufferedReader,InputStream,InputStreamReader,Reader}
import java.nio.charset.{Charset,CharsetDecoder,CodingErrorAction}
import java.sql.Connection
import scala.util.control.Exception.allCatch
import org.overviewproject.database.{ Database, DB }
import org.overviewproject.persistence.{ EncodedUploadFile, LargeObjectInputStream }
import org.overviewproject.postgres.LO


/**
 * Provides a context for reading an uploaded file from the database. The
 * reader uses the CharsetDecoder specified by the uploaded file's encoding.
 */
class UploadReader() {
  private val Utf8: String = "UTF-8"
  private val Utf8Bom: Array[Byte] = Array[Byte](0xEF.toByte, 0xBB.toByte, 0xBF.toByte)
  private val DefaultCharSet: String = Utf8

  private var countingInputStream: CountingInputStream = _

  /** @return a reader for the given UploadedFile */
  def reader(contentsOid: Long, uploadedFile: EncodedUploadFile): Reader = {
    val fileDecoder = decoder(uploadedFile.encoding)
    val largeObjectInputStream = new LargeObjectInputStream(contentsOid)
    countingInputStream = new CountingInputStream(largeObjectInputStream)

    maybeSkipUnicodeByteOrderMarker(countingInputStream, fileDecoder.charset.name)

    val inputStreamReader = new InputStreamReader(countingInputStream, fileDecoder)
    new BufferedReader(inputStreamReader)
  }

  /** Circumvents Java but http://bugs.sun.com/view_bug.do?bug_id=4508058
    *
    * Java's InputStreamReader will produce a U+FEFF, even when it's supposed
    * to drop it. So we'll drop it before passing it to the decoder.
    */
  private def maybeSkipUnicodeByteOrderMarker(stream: InputStream, charsetName: String) : Unit = {
    if (charsetName == Utf8) {
      val buf : Array[Byte] = Array(0, 0, 0)
      countingInputStream.mark(3)
      countingInputStream.read(buf)

      if (buf(0) == Utf8Bom(0) && buf(1) == Utf8Bom(1) && buf(2) == Utf8Bom(2)) {
        // drop the characters before they reach the decoder
      } else {
        stream.reset() // present them
      }
    }
  }

  /** @return the number of bytes read from the uploaded file */
  def bytesRead: Long =
    if (countingInputStream != null) countingInputStream.bytesRead
    else 0l


  /**
   * @return a CharsetDecoder defined by encoding, if present and valid.
   * If not, a UTF-8 CharsetDecoder is returned.
   */
  private def decoder(encoding: Option[String]): CharsetDecoder = {
    val charSet = encoding.flatMap { n => allCatch opt Charset.forName(n) }

    val decoder = charSet.getOrElse(Charset.forName(DefaultCharSet)).newDecoder()
    decoder.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    
  }

}
