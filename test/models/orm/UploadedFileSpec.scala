package models.orm

import org.specs2.mutable.Specification
import helpers.DbTestContext
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import java.sql.Timestamp
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UploadedFileSpec extends Specification {

  step(start(FakeApplication()))

  "UploadedFile" should {
    
    trait UploadContext extends DbTestContext {
      val now: Timestamp = new Timestamp(System.currentTimeMillis())
      val uploadedFile = UploadedFile(0l, now, 0, "content-disposition", "content-type", 100)
      
      override def setupWithDb = { uploadedFile.save }
   
    }

    "write and read to the database" in new UploadContext {
      uploadedFile.id must not be equalTo(0)
      val foundUploadedFile = UploadedFile.findById(uploadedFile.id)
      foundUploadedFile must beSome
    }

    "be deleted" in new UploadContext {
      uploadedFile.delete
      val foundUploadedFile = UploadedFile.findById(uploadedFile.id)
      foundUploadedFile must beNone
    }
  }
  step(stop)
}