package models.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.Specification
import helpers.DbTestContext
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import java.sql.Timestamp
import models.orm.Schema.uploadedFiles

class UploadedFileSpec extends Specification {

  step(start(FakeApplication()))

  "UploadedFile" should {

    trait UploadContext extends DbTestContext {
      val now: Timestamp = new Timestamp(System.currentTimeMillis())
      val uploadedFile = UploadedFile(0l, now, 0, "content-disposition", "content-type", 100)

      override def setupWithDb = {
        uploadedFiles.insertOrUpdate(uploadedFile)
      }

    }

    "read from the database" in new UploadContext {
      val foundUploadedFile = UploadedFile.findById(uploadedFile.id)
      foundUploadedFile must beSome
    }
  }
  step(stop)
}