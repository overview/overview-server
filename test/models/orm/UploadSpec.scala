package models.orm

import helpers.DbTestContext
import java.sql.Timestamp
import java.util.UUID
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

class UploadSpec extends Specification {

  step(start(FakeApplication()))

  "Upload" should {

    trait UploadContext extends DbTestContext {
      val uploadId = UUID.randomUUID
      val timestamp = new Timestamp(System.currentTimeMillis)
      val user = User()
      var upload: Upload = _
      
      override def setupWithDb = {
        Schema.users.insert(user)
	upload = Upload(0l, user.id, uploadId, timestamp, "file", 0, 100, 0)
	upload.save   
      }
    }

    "write to and read from the database" in new UploadContext {
      upload.id must not be equalTo(0)

      val foundUpload = Schema.uploads.where(u => u.id === upload.id).headOption
      foundUpload must beSome
    }

    "be findable by UUID and user_id" in new UploadContext {
      val foundUpload = Upload.findUserUpload(user.id, uploadId)
      
      foundUpload must beSome
    }


  }

  step(stop)
}
