package csv

import java.io.Reader
import database.DB
import overview.largeobject.LO
import persistence.UploadedFileLoader
import java.sql.Connection
import java.io.BufferedReader
import java.io.InputStreamReader
import overview.largeobject.LargeObject

class UploadReader(uploadedFileId: Long) {

  private var countingInputStream: CountingInputStream = _

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
      val reader = new BufferedReader(new InputStreamReader(countingInputStream))
      block(reader)
    }
  }

}