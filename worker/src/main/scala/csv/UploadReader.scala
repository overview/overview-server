/*
 * UploadReader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package csv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.sql.Connection
import scala.util.control.Exception.allCatch
import overview.database.DB
import overview.largeobject.LO
import persistence.UploadedFileLoader

/**
 * Provides a context for reading an uploaded file from the database. The 
 * reader uses the CharsetDecoder specified by the uploaded file's encoding.
 */
class UploadReader(uploadedFileId: Long) {

  private var DefaultCharSet: String = "UTF-8"
  private var countingInputStream: CountingInputStream = _
  
  /** size is only known inside the read scope */
  var size: Option[Long] = None
  
  /** @return the number of bytes read from the uploaded file */
  def bytesRead: Long =
    if (countingInputStream != null) countingInputStream.bytesRead
    else 0l

  /** 
   * Provide a scope with a Reader that can be used to read the uploaded file.
   */
  def read[T](block: Reader => T)(implicit connection: Connection): T = {
    implicit val pgc = DB.pgConnection

    val upload = UploadedFileLoader.load(uploadedFileId)
    size = Some(upload.size)

    LO.withLargeObject(upload.contentsOid) { largeObject =>
      countingInputStream = new CountingInputStream(largeObject.inputStream)
      val reader = new BufferedReader(new InputStreamReader(countingInputStream, decoder(upload.encoding)))
      block(reader)
    }
  }

  /** 
   * @return a CharsetDecoder defined by encoding, if present and valid.
   * If not, a UTF-8 CharsetDecoder is returned.
   */
  private def decoder(encoding: Option[String]): CharsetDecoder = {
    val charSet = encoding.flatMap { n => allCatch opt Charset.forName(n)  }
    
    val decoder = charSet.getOrElse(Charset.forName(DefaultCharSet)).newDecoder()
    decoder.onMalformedInput(CodingErrorAction.REPLACE)
  }
}