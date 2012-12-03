package csv

import org.overviewproject.test.DbSpecification
import org.specs2.execute.PendingUntilFixed
import overview.largeobject.LO
import overview.database.DB
import testutil.DbSetup._
import java.io.InputStreamReader
import java.nio.charset.Charset
import persistence.UploadedFile

class UploadReaderSpec extends DbSpecification {

  step(setupDb)

  "UploadReader" should {

    trait UploadContext extends DbTestContext {
      def uploadSize: Int
      def data: Array[Byte]
      def contentType: String

      var uploadReader: UploadReader = _

      override def setupWithDb = {
        implicit val pgc = DB.pgConnection
        val loid = LO.withLargeObject { lo =>
          lo.add(data)
          lo.oid
        }
        val uploadId = insertUploadedFile(loid, "content-disposition", contentType, uploadSize)
        uploadReader = new UploadReader(uploadId)
      }
    }

    trait LargeData extends UploadContext {
      def uploadSize = 10000
      def data: Array[Byte] = Array.fill(uploadSize)(74)
      def contentType = "application/octet-stream"
    }

    implicit def b(x: Int): Byte = x.toByte

    trait Windows1252Data extends UploadContext {
      def data: Array[Byte] = Array[Byte](159, 128, 154)
      val windows1252Text = new String(data, "windows-1252")
      def contentType = "application/octet-stream ; charset=windows-1252"
      def uploadSize = data.size
    }

    trait InvalidEncoding extends UploadContext {
      def uploadSize = 5
      def data: Array[Byte] = Array.fill(uploadSize)(74)
      def contentType = "application/octet-stream ; charset=notArealCharSet"
    }

    trait InvalidInput extends UploadContext {
      def uploadSize = 1
      def data: Array[Byte] = Array[Byte](255)
      def contentType = "application/octet-stream ; charset=utf-8"
    }

    "create reader from uploaded file" in new LargeData {
      uploadReader.read { reader =>
        val buffer = new Array[Char](20480)
        val numRead = reader.read(buffer)
        buffer.take(numRead) must be equalTo (data.map(_.toChar).take(numRead))
      }
    }

    "return upload size inside read block" in new LargeData {
      uploadReader.size must beNone

      uploadReader.read { reader =>
        uploadReader.size must beSome.like { case s => s must be equalTo (uploadSize) }
      }
    }

    "return bytesRead" in new LargeData {
      uploadReader.bytesRead must be equalTo (0)

      uploadReader.read { reader =>
        val buffer = new Array[Char](20480)
        val numRead = reader.read(buffer)
        uploadReader.bytesRead must be equalTo (numRead)
        reader.read(buffer)
        uploadReader.bytesRead must be equalTo (uploadSize)
      }
    }

    "read non-default encoding" in new Windows1252Data {
      uploadReader.read { reader =>
        val buffer = new Array[Char](uploadSize)
        reader.read(buffer)

        buffer must be equalTo (windows1252Text.toCharArray())
      }
    }

    "default to UTF-8 if specified encoding is not valid" in new InvalidEncoding {
      uploadReader.read { reader =>
        val buffer = new Array[Char](uploadSize)
        reader.read(buffer)
        buffer must be equalTo data.map(_.toChar)
      }
    }

    "insert replacement character for invalid input" in new InvalidInput {
      val replacement = Charset.forName("UTF-8").newDecoder.replacement.toCharArray()

      uploadReader.read { reader =>
        val buffer = new Array[Char](uploadSize)
        reader.read(buffer)

        buffer must be equalTo (replacement)
      }
    }
  }

  step(shutdownDb)
}