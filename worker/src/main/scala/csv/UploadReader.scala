package csv

import java.io.Reader
import java.nio.charset.Charset
import database.DB
import overview.largeobject.LO
import persistence.UploadedFileLoader
import java.sql.Connection
import java.io.BufferedReader
import java.io.InputStreamReader
import overview.largeobject.LargeObject
import scala.util.control.Exception.allCatch
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

class UploadReader(uploadedFileId: Long) {

  private var countingInputStream: CountingInputStream = _
  private var DefaultCharSet: String = "UTF-8"
    
  var size: Option[Long] = None
  def bytesRead: Long =
    if (countingInputStream != null) countingInputStream.bytesRead
    else 0l

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

  private def decoder(encoding: Option[String]): CharsetDecoder = {
    val charSet = encoding.flatMap { n => allCatch opt Charset.forName(n)  }
    
    val decoder = charSet.getOrElse(Charset.forName(DefaultCharSet)).newDecoder()
    decoder.onMalformedInput(CodingErrorAction.REPLACE)
  }
}