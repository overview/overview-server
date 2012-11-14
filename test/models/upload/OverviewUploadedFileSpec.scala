package models.upload

import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import helpers.PgConnectionContext
import java.sql.Timestamp
import play.api.test.FakeApplication

@RunWith(classOf[JUnitRunner])
class OverviewUploadedFileSpec extends Specification {

  step(start(FakeApplication()))

  "OverviewUploadedFile" should {

    trait UploadedFileContext extends PgConnectionContext {
      var overviewUploadedFile: OverviewUploadedFile = _
      var before: Timestamp = _

      override def setupWithDb = LO.withLargeObject { lo =>
        before = new Timestamp(System.currentTimeMillis)
        overviewUploadedFile = OverviewUploadedFile(lo.oid, "content-disposition", "content-type")
      }
    }

    "set uploadedAt time on creation" in new UploadedFileContext {
      overviewUploadedFile.uploadedAt.compareTo(before) must be greaterThanOrEqualTo (0)
    }

    "withSize results in updated size and time uploadedAt" in new UploadedFileContext {
      val uploadedFileWithNewSize = overviewUploadedFile.withSize(100)
      uploadedFileWithNewSize.uploadedAt.compareTo(overviewUploadedFile.uploadedAt) must be greaterThanOrEqualTo (0)
    }

    "withContentInfo sets contentDisposition and contentType" in new UploadedFileContext {
      val newDisposition = "new disposition"
      val newType = "new type"

      val uploadedFileWithNewContentInfo = overviewUploadedFile.withContentInfo(newDisposition, newType)
      uploadedFileWithNewContentInfo.contentDisposition must be equalTo newDisposition
      uploadedFileWithNewContentInfo.contentType must be equalTo newType
    }

    "be saveable and findable by id" in new UploadedFileContext {
      val savedUploadedFile = overviewUploadedFile.save
      savedUploadedFile.id must not be equalTo(0)

      val foundUploadedFile = OverviewUploadedFile.findById(savedUploadedFile.id)

      foundUploadedFile must beSome
    }

    "be deleted" in new UploadedFileContext {
      val savedUploadedFile = overviewUploadedFile.save
      savedUploadedFile.delete
      val foundUploadedFile = OverviewUploadedFile.findById(savedUploadedFile.id)

      foundUploadedFile must beNone
    }

  }
  step(stop)
}