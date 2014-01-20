package org.overviewproject.util

import com.google.common.base.Charsets
import com.google.common.escape.UnicodeEscaper
import com.google.common.base.CharMatcher
import java.nio.charset.Charset

object Textify {
  private val surrogateCharRegex = "[\ud800-\udfff]".r

  private object Escaper extends UnicodeEscaper {
    private val Newline = Array[Char]('\n')
    private val Space = Array[Char](' ')
    private val Empty = Array[Char]()

    override protected def escape(cp: Int) : Array[Char] = {
      cp match {
        case 0x9 => null
        case 0xa => null
        case 0xd => null
        case 0x85 => Newline
        case i if Character.isISOControl(i) => Space
        case i if (i & 0xfffe) == 0xfffe => Empty
        case i if (i >= 0xfdd0 && i < 0xfdf0) => Empty
        case _ => null
      }
    }

    override protected def nextEscapeIndex(csq: CharSequence, start: Int, end: Int) : Int = {
      Range(start, end).find({ i : Int =>
        val c = csq.charAt(i)

        c != 0x9 &&
          c != 0xa &&
          c != 0xd &&
          (
            c.isControl ||
            c.isSurrogate || // easy handling of UTF-16 encoding: let Guava handle the confusion
            c == 0xfffe ||
            c == 0xffff ||
            (c >= 0xfdd0 && c < 0xfdf0)
          )
      }).getOrElse(end)
    }
  }

  private def unifyNewlines(text: String) : String = {
    // This method is special: it's the only place where we fold two Unicode
    // codepoints ("\r\n") into one ("\n").
    val newlineRegex = """(\r\n|\r)""".r
    newlineRegex.replaceAllIn(text, "\n")
  }

  /** Parses UTF-8 as Java 8 would.
    *
    * TL;DR: use this when your input might be invalid. If you use
    * `new String(bytes, "utf-8")`, your program can crash on invalid input.
    *
    * Java 7'd utf-8 decoder allows CESU-8 encoding. This is error-prone: it
    * can decode unpaired surrogates, which in turn break the assumptions in
    * Java's String class.
    *
    * See https://bugs.openjdk.java.net/browse/JDK-7096080
    *
    * Since Unicode surrogate characters aren't utf-8 anyway, we should inject
    * their replacement characters.
    * See http://www.cl.cam.ac.uk/~mgk25/ucs/examples/UTF-8-test.txt
    *
    * Here's an example: U+D800 U+DC00:
    * 
    *     val bytes = Array[Int](0xed, 0xa0, 0x80, 0xed, 0xb0, 0x80).map(_.toByte)
    *     new String(bytes, Charsets.UTF_8) // => U+10000
    *     Textify.parseUtf8(bytes) // => U+FFFD U+FFFD U+FFFD U+FFFD U+FFFD U+FFFD
    *
    *     // Why would it be harmless to parse the array as U+10000?
    *     // because:
    *     val broken = new String(bytes.take(3), Charsets.UTF_8) // => U+D8000
    *     val notBroken = Textify.parseUtf8(bytes.take(3)) // U+FFFD U+FFFD U+FFFD
    *     broken.codePointAt(0) // => java.lang.StringIndexOutOfBoundsException
    *     notBroken.codePointAt(0) // => 0xfffd
    */
  private def parseUtf8(rawBytes: Array[Byte]) : String = {
    val broken = new String(rawBytes, Charsets.UTF_8)
    surrogateCharRegex.replaceAllIn(broken, "\ufffd\ufffd\ufffd")
  }

  /** Returns text from a given Unicode string.
    *
    * Unicode strings can contain non-text characters: control characters, null
    * characters and noncharacters, for instance.
    *
    * This method turns any of those into text: a sequence of characters as in
    * XML 1.0: http://www.w3.org/TR/REC-xml/#dt-character
    *
    * Here's how the output may differ from the input:
    *
    * * Newlines are unified: \r, \n, \r\n and \u0085 all become \n.
    * * Control characters (except for \t) are transformed into spaces. (This
    *   is so word boundaries won't change.)
    * * Noncharacters are deleted. (U+FFFE, the byte-order marker, disappears.)
    * * Solitary surrogates (the byte sequences that represent multi-byte
    *   characters) cannot occur in Java Strings, so they are not handled.
    */
  def apply(rawText: String) : String = {
    val escaped = Escaper.escape(rawText)
    unifyNewlines(escaped)
  }

  /** Returns text from a given array of bytes.
    *
    * This special String decoder does the following:
    *
    * * Decodes from the given charset (to a Java String).
    * * Replaces invalid input characters with U+FFFD (so it will not crash).
    * * Replaces unpaired surrogates with U+FFFD U+FFFD U+FFFD (again, so it
    *   will not crash -- Java's decoder does not do this, and Strings crash as
    *   a result).
    * * Replaces control characters (except for \t) with spaces (so word
    *   boundaries won't change).
    * * Deletes noncharacters (so U+FFFE, the byte-order marker, disappears).
    * * Unifies newlines: \r, \r\n and \u0085 become \n.
    */
  def apply(rawBytes: Array[Byte], charset: Charset) : String = {
    charset match {
      case Charsets.UTF_8 => apply(parseUtf8(rawBytes))
      case _ => apply(new String(rawBytes, charset))
    }
  }
}
