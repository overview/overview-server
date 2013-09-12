package org.overviewproject.util

import java.net.URLDecoder
import scala.util.control.Exception._

object ContentDisposition {
  // Based on behavior described by
  // https://github.com/rack/rack/blob/master/lib/rack/multipart.rb
  private def findFilename(contentDisposition: String): Option[String] = {
    val Token = """[^\s()<>,;:\\"\/\[\]?=]+"""
    val ConDisp = """\s*%s\s*""".format(Token)
    def DispParam(key: String, groupValue: Boolean) = {
      val captureGroup = if (!groupValue) "?:" else ""
      """;\s*(?:%s)=(%s"(?:\\"|[^"])*"|%s)*""".format(key, captureGroup, Token)
    }

    val FilenameParam = DispParam(key = "(?i)filename", groupValue = true).r
    val BrokenQuoted = """^%s.*;\s(?i)filename="([^"]*)"(?:\s*$|\s*;\s*%s=).*""".format(ConDisp, Token).r
    val BrokenUnquoted = """^%s.*;\s(?i)filename=(%s).*""".format(ConDisp, Token).r
    val Rfc2183 = """^%s((?:%s)+)$""".format(ConDisp, DispParam(key = Token, groupValue = false)).r

    contentDisposition match {
      case Rfc2183(p) => FilenameParam.findFirstMatchIn(p).map(_.group(1))
      case BrokenUnquoted(f) => Some(f)
      case BrokenQuoted(f) => Some(f)
      case _ => None
    }
  }

  private def stripQuotes(filename: String): String = {
    val QuotedString = """^".*"$"""
    // Using regexp to extract filename results in escaped quotes being unescaped
    if (filename.matches(QuotedString)) filename.substring(1, filename.length - 1)
    else filename
  }

  private def unescapeQuotes(filename: String): String = {
    val EscapedChar = """\\([\\"])""".r
    EscapedChar.replaceAllIn(filename, m => m.group(0))
  }

  private def decode(filename: String): Option[String] = {
    allCatch opt { URLDecoder.decode(filename, "UTF-8") }
  }

  def filename(contentDisposition: String): Option[String] =
    findFilename(contentDisposition) flatMap { n =>
      decode(
        unescapeQuotes(
          stripQuotes(n)))
    }
}