/*
 * UploadReader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.sql.Connection
import scala.util.control.Exception.allCatch

import org.overviewproject.database.DB
import org.overviewproject.postgres.LO
import persistence.UploadedFileLoader
import org.overviewproject.database.Database
import persistence.LargeObjectInputStream

/**
 * Provides a context for reading an uploaded file from the database. The
 * reader uses the CharsetDecoder specified by the uploaded file's encoding.
 */
class UploadReader(uploadedFileId: Long) {

  private var DefaultCharSet: String = "UTF-8"
  private var countingInputStream: CountingInputStream = _

  var size: Option[Long] = None
  var reader: Reader = _

  /** @return the number of bytes read from the uploaded file */
  def bytesRead: Long =
    if (countingInputStream != null) countingInputStream.bytesRead
    else 0l

 
  /**
   * Must be called before reader is accessed. So ugly.
   */
  def initializeReader {
    implicit val c = Database.currentConnection

    val upload = UploadedFileLoader.load(uploadedFileId)
    size = Some(upload.size)

    val largeObjectInputStream = new LargeObjectInputStream(upload.contentsOid)
    countingInputStream = new CountingInputStream(largeObjectInputStream)

    reader = new BufferedReader(new InputStreamReader(countingInputStream, decoder(upload.encoding)))
  }
  
  /**
   * @return a CharsetDecoder defined by encoding, if present and valid.
   * If not, a UTF-8 CharsetDecoder is returned.
   */
  private def decoder(encoding: Option[String]): CharsetDecoder = {
    val charSet = encoding.flatMap { n => allCatch opt Charset.forName(n) }

    val decoder = charSet.getOrElse(Charset.forName(DefaultCharSet)).newDecoder()
    decoder.onMalformedInput(CodingErrorAction.REPLACE)
  }
  
}