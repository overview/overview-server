package models.upload

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
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
        overviewUploadedFile = OverviewUploadedFile(lo.oid, "attachment; filename=name", "content-type")
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

  "OverviewUploadedFile filename" should {

    "return filename from content-disposition" in {
      val name = "file.name"
      val overviewUploadedFile = OverviewUploadedFile(0, "attachment; filename=" + name, "content-type")
      overviewUploadedFile.filename must be equalTo (name)
    }

    "return Upload <date> if no content-disposition found" in {
      val overviewUploadedFile = OverviewUploadedFile(0, "bad-content-disposition", "content-type")
      val now = new Timestamp(System.currentTimeMillis())
      val defaultFilename = "Upload " + now
      // bad test - only checks that timestamp specifies today's date
      // could fail if executed at midnight.
      overviewUploadedFile.filename.take(17) must be equalTo(defaultFilename.take(17))
    }
  }
}