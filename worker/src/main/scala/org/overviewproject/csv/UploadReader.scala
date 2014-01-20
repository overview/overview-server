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
import org.overviewproject.persistence.EncodedUploadFile
import org.overviewproject.postgres.{ LO, LargeObjectInputStream }


/**
 * Provides a context for reading an uploaded file from the database. The
 * reader uses the CharsetDecoder specified by the uploaded file's encoding.
 */
class UploadReader() {
  private val Utf8: String = "UTF-8"
  private val DefaultCharSet: String = Utf8

  private var countingInputStream: CountingInputStream = _

  /** @return a reader for the given UploadedFile */
  def reader(contentsOid: Long, uploadedFile: EncodedUploadFile): Reader = {
    val fileDecoder = decoder(uploadedFile.encoding)
    val largeObjectInputStream = new LargeObjectInputStream(contentsOid)
    countingInputStream = new CountingInputStream(largeObjectInputStream)

    val inputStreamReader = new InputStreamReader(countingInputStream, fileDecoder)
    new BufferedReader(inputStreamReader)
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
