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

    trait DispositionParameter {
      val name: String
      lazy val dispParams: String = "filename=%s".format(name)
    }

    trait SimpleParameter extends DispositionParameter {
      val name = "afile.foo"
    }

    trait MultipleParameters extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = "param1=value1; filename=%s; param2=value2".format(name)
    }

    trait FilenameInQuotes extends DispositionParameter {
      val name = """"afile.foo""""
    }

    trait FilenameWithEscapedQuote extends DispositionParameter {
      val name = """"quote\"me\"""""
    }

    trait UnfortunatelyAccepted extends DispositionParameter {
      val name = """hi"and"bye"""
    }

    trait FilenameFollowedByGarbage extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = "filename=%s;blablabla".format(name)	  
    }

    trait ContentDispositionContext extends Scope {
      self: DispositionParameter =>

      def contentDisposition = "attachment; " + dispParams

      lazy val overviewUploadedFile = new TestOverviewUploadedFile(contentDisposition)
    }

    "find filename in simplest possible case" in new ContentDispositionContext with SimpleParameter {
      overviewUploadedFile.filename must be equalTo (name)
    }

    "find filename among multiple parameters (RFC2183)" in new ContentDispositionContext with MultipleParameters {
      overviewUploadedFile.filename must be equalTo (name)
    }

    "find quoted filename" in new ContentDispositionContext with FilenameInQuotes {
      overviewUploadedFile.filename must be equalTo (name)
    }

    "find filename with escaped quotes" in new ContentDispositionContext with FilenameWithEscapedQuote {
      overviewUploadedFile.filename must be equalTo (name)
    }

    "accept the unacceptable" in new ContentDispositionContext with UnfortunatelyAccepted {
      overviewUploadedFile.filename must not be equalTo("")
    }

    "extract filename parameter followed by garbage" in new ContentDispositionContext with FilenameFollowedByGarbage {
      overviewUploadedFile.filename must be equalTo (name)
    }
  }
}