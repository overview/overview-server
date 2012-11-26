package models.upload

import org.specs2.mutable.Specification
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import java.sql.Timestamp
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class ContentDispositionSpec extends Specification {
  "OverviewUploadedFile contentDisposition" should {

    class TestOverviewUploadedFile(val contentDisposition: String) extends OverviewUploadedFile {
      val id: Long = 0
      val uploadedAt: Timestamp = new Timestamp(0)
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
      val unquotedName = "afile.foo"
      val name = """"%s"""".format(unquotedName)
    }

    trait FilenameWithEscapedQuote extends DispositionParameter {
      val unquotedName = """quote\"me\""""
      val name = """"%s"""".format(unquotedName)
    }

    trait UnfortunatelyAccepted extends DispositionParameter {
      val name = """hi"and"bye"""
    }

    trait FilenameFollowedByGarbage extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = "filename=%s;blablabla".format(name)
    }

    trait QuotedFilenameFollowedByGarbage extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = """filename="%s" ;blablabla=""".format(name)
    }

    trait ValidMixedCase extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = "FileName=%s".format(name)
    }

    trait MixedCaseFollowedByGarbage extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = "filEnamE=%s;blablabla".format(name)
    }

    trait MixedCaseWithQuotedFilenameFollowedByGarbage extends DispositionParameter {
      val name = "afile.foo"
      override lazy val dispParams: String = """FILENAME="%s" ;blablabla=""".format(name)
    }

    trait UrlEncodedName extends DispositionParameter {
      val name = """"%68%65%6C%6C%6F"""" // "hello"
    }

    trait InvalidEncoding extends DispositionParameter {
      val name = """"%XY%4-b""""
    }

    trait ContentDispositionContext extends Scope {
      self: DispositionParameter =>

      def contentDisposition = "attachment; " + dispParams

      lazy val overviewUploadedFile = new TestOverviewUploadedFile(contentDisposition)
    }

    "find filename in simplest possible case" in new ContentDispositionContext with SimpleParameter {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }

    "find filename among multiple parameters (RFC2183)" in new ContentDispositionContext with MultipleParameters {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }

    "find quoted filename" in new ContentDispositionContext with FilenameInQuotes {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (unquotedName) }
    }

    "find filename with escaped quotes" in new ContentDispositionContext with FilenameWithEscapedQuote {
      ContentDisposition.filename(contentDisposition) must beSome.like {
        case n =>
          n must be equalTo (unquotedName.replaceAllLiterally("""\""", ""))
      }
    }

    "accept the unacceptable" in new ContentDispositionContext with UnfortunatelyAccepted {
      ContentDisposition.filename(contentDisposition) must beSome
    }

    "find filename parameter followed by garbage" in new ContentDispositionContext with FilenameFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }

    "find quoted filename parameter followed by garbage" in new ContentDispositionContext with QuotedFilenameFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }

    "Decoded encoded filename" in new ContentDispositionContext with UrlEncodedName {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo ("hello") }
    }

    "Handle invalid encoding" in new ContentDispositionContext with InvalidEncoding {
      ContentDisposition.filename(contentDisposition) must beNone
    }

    "Handle mixed case - RFC2183" in new ContentDispositionContext with ValidMixedCase {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }

    "Handle mixed case - Broken Unquoted" in new ContentDispositionContext with MixedCaseFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }

    "Handle mixed case - Broken quoted" in new ContentDispositionContext with MixedCaseWithQuotedFilenameFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome.like { case n => n must be equalTo (name) }
    }
  }

}