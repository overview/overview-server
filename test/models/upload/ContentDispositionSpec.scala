package models.upload

import org.specs2.mutable.Specification
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import java.sql.Timestamp
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class ContentDispositionSpec extends Specification {
  "OverviewUploadedFile contentDisposition" should {

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
    }

    "find filename in simplest possible case" in new ContentDispositionContext with SimpleParameter {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }

    "find filename among multiple parameters (RFC2183)" in new ContentDispositionContext with MultipleParameters {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }

    "find quoted filename" in new ContentDispositionContext with FilenameInQuotes {
      ContentDisposition.filename(contentDisposition) must beSome(unquotedName)
    }

    "find filename with escaped quotes" in new ContentDispositionContext with FilenameWithEscapedQuote {
      ContentDisposition.filename(contentDisposition) must beSome(unquotedName.replaceAllLiterally("""\""", ""))
    }

    "accept the unacceptable" in new ContentDispositionContext with UnfortunatelyAccepted {
      ContentDisposition.filename(contentDisposition) must beSome
    }

    "find filename parameter followed by garbage" in new ContentDispositionContext with FilenameFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }

    "find quoted filename parameter followed by garbage" in new ContentDispositionContext with QuotedFilenameFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }

    "Decoded encoded filename" in new ContentDispositionContext with UrlEncodedName {
      ContentDisposition.filename(contentDisposition) must beSome("hello")
    }

    "Handle invalid encoding" in new ContentDispositionContext with InvalidEncoding {
      ContentDisposition.filename(contentDisposition) must beNone
    }

    "Handle mixed case - RFC2183" in new ContentDispositionContext with ValidMixedCase {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }

    "Handle mixed case - Broken Unquoted" in new ContentDispositionContext with MixedCaseFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }

    "Handle mixed case - Broken quoted" in new ContentDispositionContext with MixedCaseWithQuotedFilenameFollowedByGarbage {
      ContentDisposition.filename(contentDisposition) must beSome(name)
    }
  }

}
