package csv

import helpers.DbSpecification
import org.specs2.execute.PendingUntilFixed
import overview.largeobject.LO
import database.DB
import testutil.DbSetup._
import java.io.InputStreamReader

class UploadReaderSpec extends DbSpecification {

  step(setupDb)

  "UploadInputStream" should {

    "create reader from uploaded file" in new DbTestContext {
      val data = Array[Byte](74, 79, 78, 75)
      implicit val pgc = DB.pgConnection
      val loid = LO.withLargeObject { lo =>
        lo.add(data)
        lo.oid
      }
      val uploadId = insertUploadedFile(loid, "content-disposition", "content-type", 100)

      val uploadReader = new UploadReader(uploadId)
      uploadReader.read { reader =>
        val buffer = new Array[Char](128)
        val numRead = reader.read(buffer)
        numRead must be equalTo(data.size)
        buffer.take(numRead) must be equalTo(data.map(_.toChar))
      }
    }
  }

  step(shutdownDb)
}