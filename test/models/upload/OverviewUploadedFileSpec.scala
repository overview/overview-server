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
import org.specs2.mock.Mockito

@RunWith(classOf[JUnitRunner])
class OverviewUploadedFileSpec extends Specification with Mockito {

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

  "OverviewUploadedFile contentDisposition" should {

    class TestOverviewUploadedFile(val contentDisposition: String) extends OverviewUploadedFile {
      val id: Long = 0
      val uploadedAt: Timestamp = null
      val contentsOid: Long = 0
      val contentType: String = ""
      val size: Long = 0

      def withSize(size: Long): OverviewUploadedFile = this
      def withContentInfo(contentDisposition: String, contentType: String): OverviewUploadedFile = this
      def save: OverviewUploadedFile = this
      def delete {}
    }

    trait ContentDispositionContext extends Scope {
      def dispParams: String
      def contentDisposition = "attachment; " + dispParams

      lazy val overviewUploadedFile = new TestOverviewUploadedFile(contentDisposition)
    }

    trait SimpleContentDisposition extends ContentDispositionContext {
      val name = "afile.foo"
      def dispParams: String = "filename=" + name
    }

    trait MultipleDispParams extends ContentDispositionContext {
      val name = "afile.foo"
      def dispParams: String = "param1=value1; filename=%s; param2=value2".format(name)
    }

    "find filename in simplest possible case" in new SimpleContentDisposition {
      overviewUploadedFile.filename must be equalTo (name)
    }
    
    "find filename among multiple parameters (RFC2183)" in new MultipleDispParams {
      overviewUploadedFile.filename must be equalTo (name)
    }
  }
}