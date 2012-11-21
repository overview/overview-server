package csv

import helpers.DbSpecification
import org.specs2.execute.PendingUntilFixed
import overview.largeobject.LO
import database.DB
import testutil.DbSetup._
import java.io.InputStreamReader
import persistence.UploadedFile

class UploadReaderSpec extends DbSpecification {

  step(setupDb)

  "UploadReader" should {

    trait UploadContext extends DbTestContext {
      val data = Array[Byte](74, 79, 78, 75)
      val uploadSize = 100l

      var uploadReader: UploadReader = _

      override def setupWithDb = {
        implicit val pgc = DB.pgConnection
        val loid = LO.withLargeObject { lo =>
          lo.add(data)
          lo.oid
        }
        val uploadId = insertUploadedFile(loid, "content-disposition", "content-type", uploadSize)
        uploadReader = new UploadReader(uploadId)
      }
    }

    "create reader from uploaded file" in new UploadContext {
      uploadReader.read { reader =>
        val buffer = new Array[Char](128)
        val numRead = reader.read(buffer)
        numRead must be equalTo (data.size)
        buffer.take(numRead) must be equalTo (data.map(_.toChar))
      }
    }

    "return upload size inside read block" in new UploadContext {
      uploadReader.size must beNone

      uploadReader.read { reader =>
        uploadReader.size must beSome.like { case s => s must be equalTo (uploadSize) }
      }
    }
  }

  step(shutdownDb)
}