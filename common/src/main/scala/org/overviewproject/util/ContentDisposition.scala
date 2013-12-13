package org.overviewproject.util

import java.net.URLDecoder
import scala.util.control.Exception._
import scala.util.parsing.combinator.RegexParsers

case class ContentDisposition(contentDisposition: String) {
  val parseResult : Option[Map[String,String]] = Rfc5987Parser.parse(contentDisposition)
  def filename: Option[String] = parseResult.flatMap(_.get("filename"))
  def modificationDate: Option[String] = parseResult.flatMap(_.get("modification-date"))

  object Rfc5987Parser {
    def parse(input: String) : Option[Map[String,String]] = {
      Parsers.parseAll(Parsers.disposition, input) match {
        case Parsers.Success(result, _) => Some(result)
        case failure: Parsers.NoSuccess => None
      }
    }

    object Parsers extends RegexParsers {
      // http://tools.ietf.org/html/rfc5987#page-3
      // http://tools.ietf.org/html/rfc2616#section-2.2
      // http://tools.ietf.org/html/rfc2183
      // tests: http://greenbytes.de/tech/tc2231/
      override def skipWhitespace : Boolean = false

      def LWSP : Parser[String] = regex("""((\r\n)?[ \t]*)*""".r)

      // "filename=blah.txt" (with or without quotes) -> Tuple("filename", "blah.txt")
      def regParameter : Parser[Option[(String,String)]]
        = parmname ~ (LWSP ~> '=' ~> LWSP ~> value) ^^
        { kv => Some(kv._1 -> kv._2) }

      // "filename*=UTF-8''blah.txt" -> Tuple("filename", "blah.txt")
      def extParameter : Parser[Option[(String,String)]]
        = parmname ~ ('*' ~> LWSP ~> '=' ~> LWSP ~> extValue) ^^
        { kv => Some(kv._1 -> kv._2) }

      def parmname = attrChars

      // a String value like "blah.txt", from "blah.txt" or "\"blah.txt\""
      def value : Parser[String]
        = brokenMoreAcceptingToken | quotedString | brokenEmptiness

      // spec deviation. Sometimes web browsers will encode filenames
      // with '"' in strings or "\" as an escape char (which doesn't work).
      // We accept both, and we ignore the escape char but we don't treat it
      // as an escape char because that's weird.
      def brokenMoreAcceptingToken : Parser[String]
        = regex("""[^"\s()<>,;:\/\[\]?=][^\s()<>,;:\/\[\]?=]*""".r) ^^
        { (s: String) => s.replace("\\", "") }

      // spec deviation. Sometimes filenames will be the empty string. It's illegal, but we pass it through.
      def brokenEmptiness = literal("")

      // a String value like "blah.txt", from "UTF-8''blah.txt"
      def extValue : Parser[String]
        = literal("UTF-8''") ~> valueChars // no language or charset choices

      // a percent-decoded string of Bytes, then UTF-8-decoded
      def valueChars : Parser[String]
        = rep((pctEncoded ^^ { Array(_) }) | (attrChars ^^ { _.getBytes })) ^^
        { (byteArrays: List[Array[Byte]]) => new String(byteArrays.toBuffer.flatten.toArray, "utf-8") }

      // a character, percent-decoded, as a String
      def pctEncoded : Parser[Byte]
        = '%' ~> regex("""[0-9a-fA-F]{2}""".r) ^^
        { (hex: String) => Integer.parseInt(hex, 16).toByte }

      // a bunch of characters, as a String
      def token : Parser[String]
        = regex("""[^\s()<>,;:\\"\/\[\]?=]+""".r)

      // a bunch of ASCII characters, as a String (attrChar, in bulk), lowercase
      def attrChars : Parser[String]
        = regex("""[a-zA-Z0-9!#$&+\-\.^_`|~]+""".r) ^^
        { _.toLowerCase }

      // the contents of a quoted string, unescaped
      def quotedString : Parser[String]
        = '"' ~> regex("""((\\.)|(\"\")|[\s!#-\[\]-~]+)*""".r) <~ '"' ^^ // any ASCII except CTL or "; use \ and "" for escaping
        { (s: String) => s.replace("\\", "").replace("\"\"", "\"") } // unescape

      // spec deviation. sometimes there will be trailing tokens in input. We need to ignore them.
      def brokenParameter : Parser[Option[(String,String)]] = token ^^^ None

      // ("filename" -> "blah.txt")
      def parameter : Parser[Option[(String,String)]] = regParameter | extParameter | brokenParameter

      // Map(("filename" -> "blah.txt"))
      def parameters = rep(regex(" *; *".r) ~> parameter) ^^ { _.flatten.toMap }

      // key -> value pairs
      def disposition : Parser[Map[String,String]]
        = phrase(regex("""(inline|attachment)""".r) ~> parameters)
    }
  }
}
