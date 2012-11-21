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

  def read[T](block: Reader => T)(implicit connection: Connection): T = {
    implicit val pgc = DB.pgConnection

    val upload = UploadedFileLoader.load(uploadedFileId)

    LO.withLargeObject(upload.contentsOid) { largeObject =>
      val reader = new BufferedReader(new InputStreamReader(largeObject.inputStream))
      block(reader)
    }
  }

}