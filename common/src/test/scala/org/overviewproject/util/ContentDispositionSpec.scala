package org.overviewproject.util

import java.net.URLEncoder
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ContentDispositionSpec extends Specification {
  "OverviewUploadedFile contentDisposition" should {
    // http://greenbytes.de/tech/tc2231/

    trait EncodingScope extends Scope {
      def check(filename: String, expectedResult: String) = {
        val cd = ContentDisposition.fromFilename(filename)
        cd.contentDisposition must beEqualTo(expectedResult)
      }
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

    trait IncludesModifiedDateParameter extends DispositionParameter {
      val name="file.ext"
      val modificationDate = "Wed, 09 Oct 2013 05:42:00 -0500"
            
      override lazy val dispParams: String = """filename=%s ; modification-date="%s"""".format(name, modificationDate)
    }

    trait ContentDispositionContext extends Scope {
      self: DispositionParameter =>

      def contentDisposition = "attachment; " + dispParams
    }

    "find filename in simplest possible case" in new ContentDispositionContext with SimpleParameter {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "find filename among multiple parameters (RFC2183)" in new ContentDispositionContext with MultipleParameters {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "find quoted filename" in new ContentDispositionContext with FilenameInQuotes {
      ContentDisposition(contentDisposition).filename must beSome(unquotedName)
    }

    "find filename with escaped quotes" in new ContentDispositionContext with FilenameWithEscapedQuote {
      ContentDisposition(contentDisposition).filename must beSome(unquotedName.replaceAllLiterally("""\""", ""))
    }

    "accept the unacceptable" in new ContentDispositionContext with UnfortunatelyAccepted {
      ContentDisposition(contentDisposition).filename must beSome
    }

    "find filename parameter followed by garbage" in new ContentDispositionContext with FilenameFollowedByGarbage {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "find quoted filename parameter followed by garbage" in new ContentDispositionContext with QuotedFilenameFollowedByGarbage {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "not decode encoded filename in quotes" in new ContentDispositionContext with DispositionParameter {
      val name = """"%68%65%6C%6C%6F"""" // "hello"
      ContentDisposition(contentDisposition).filename must beSome("%68%65%6C%6C%6F")
    }

    "Handle mixed case - RFC2183" in new ContentDispositionContext with ValidMixedCase {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "Handle mixed case - Broken Unquoted" in new ContentDispositionContext with MixedCaseFollowedByGarbage {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "Handle mixed case - Broken quoted" in new ContentDispositionContext with MixedCaseWithQuotedFilenameFollowedByGarbage {
      ContentDisposition(contentDisposition).filename must beSome(name)
    }
    
    "Find modification date parameter" in new ContentDispositionContext with IncludesModifiedDateParameter {
      ContentDisposition(contentDisposition).modificationDate must beSome(modificationDate)
    }

    "Handle UTF-8 filenames using RFC2231/5987 encoded UTF-8" in new ContentDispositionContext with DispositionParameter {
      // http://greenbytes.de/tech/tc2231/#attwithfn2231utf8
      val name = "元気なですか？.pdf"
      override lazy val dispParams: String = """attachment; filename*=UTF-8''%E5%85%83%E6%B0%97%E3%81%AA%E3%81%A7%E3%81%99%E3%81%8B%EF%BC%9F.pdf"""

      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    "Decode mixed-case filenames" in new ContentDispositionContext with DispositionParameter {
      override val name = "FILE,name.pdf"
      override lazy val dispParams: String = """attachment; filename*=UTF-8''FILE%2Cname.pdf"""
      ContentDisposition(contentDisposition).filename must beSome(name)
    }

    // Encoding tests are from test/assets/javascripts/specs/util/net/upload-spec.coffee
    "Encode a filename" in new EncodingScope {
      check("filename.txt", "attachment; filename=\"filename.txt\"")
    }

    "Encode a UTF-8 filename" in new EncodingScope {
      check("元気なですか？.pdf", "attachment; filename*=UTF-8''%E5%85%83%E6%B0%97%E3%81%AA%E3%81%A7%E3%81%99%E3%81%8B%EF%BC%9F.pdf")
    }

    "Encode an even slightly not-HTTP-friendly filename" in new EncodingScope {
      check("file,name.txt", "attachment; filename*=UTF-8''file%2Cname.txt")
    }

    "Encode capitals" in new EncodingScope {
      check("FILE,name.txt", "attachment; filename*=UTF-8''FILE%2Cname.txt")
    }

    "Encode pipe, caret or backtick as-is" in new EncodingScope {
      check("file*|^`name.txt", "attachment; filename*=UTF-8''file%2A|^`name.txt")
    }
  }

}
