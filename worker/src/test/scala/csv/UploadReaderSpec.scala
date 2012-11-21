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
      val uploadSize = 10000 // large enough so to fill read's 8k buffer
      
      val data: Array[Byte] = Array.fill(uploadSize)(74)

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
        val buffer = new Array[Char](20480)
        val numRead = reader.read(buffer)
        buffer.take(numRead) must be equalTo (data.map(_.toChar).take(numRead))
      }
    }

    "return upload size inside read block" in new UploadContext {
      uploadReader.size must beNone

      uploadReader.read { reader =>
        uploadReader.size must beSome.like { case s => s must be equalTo (uploadSize) }
      }
    }
    
    "return bytesRead" in new UploadContext {
      uploadReader.bytesRead must be equalTo(0)
      
      uploadReader.read { reader =>
        val buffer = new Array[Char](20480)
        val numRead = reader.read(buffer)
        uploadReader.bytesRead must be equalTo(numRead)
        reader.read(buffer)
        uploadReader.bytesRead must be equalTo(uploadSize)
      }
    }
  }

  step(shutdownDb)
}