package org.overviewproject.util

import com.google.common.base.Charsets
import java.nio.charset.Charset
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class TextifySpec extends Specification {
  "Textify" should {
    def t(rawText: String, expected: String, message: String) : Unit = {
      message in {
        Textify(rawText) must beEqualTo(expected)
      }
    }

    def b(charset: Charset, rawBytes: Array[Byte], expected: String, message: String) : Unit = {
      message in {
        Textify(rawBytes, charset) must beEqualTo(expected)
      }
    }

    t("foobar", "foobar", "leave ASCII alone")
    t("line1\rline2\nline3\r\nline4", "line1\nline2\nline3\nline4", "translate newlines to \\n")
    t("line1\u0085line2", "line1\nline2", "translate NEL to \\n")
    t("foo\u0009bar", "foo\u0009bar", "not translate tabs")
    t("foo\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000b\u000c\u000e\u000fbar",
      "foo             bar",
      "replace ASCII control chars 0x0-0x8,0xb,0xc,0xe,0xf with spaces")
    t("foo\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001fbar",
      "foo                bar",
      "replace ASCII control chars 0x10-0x1f with spaces")
    t("foo\u007f\u0080\u0081\u0082\u0083\u0084\u0086\u0087\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009a\u009b\u009c\u009d\u009e\u009fbar",
      "foo                                bar",
      "replace ASCII control chars 0x7f-0x84, 0x86-0x9f with spaces")
    t("foo\ufdd0\ufdd1\ufdd2\ufdd3\ufdd4\ufdd5\ufdd6\ufdd7\ufdd8\ufdd9\ufdda\ufddb\ufddc\ufddd\ufdde\ufddf\ufde0\ufde1\ufde2\ufde3\ufde4\ufde5\ufde6\ufde7\ufde8\ufde9\ufdea\ufdeb\ufdec\ufded\ufdee\ufdefbar",
      "foobar",
      "remove Unicode non-characters (U+FDD0 .. U+FDEF)")
    t("foo\ufdcf\ufdf0bar", "foo\ufdcf\ufdf0bar", "not remove actual characters near U+FDD0 .. U+FDEF")
    t("foo\ufffdbar", "foo\ufffdbar", "not remove U+FFFD")

    "remove Unicode non-characters (U+FFFE, U+FFFF, U+1FFFE, U+1FFFF, ...)" in {
      val sb = new StringBuffer("\ufffe\uffff")
      Range(0x1, 0x11, 1).foreach { b : Int =>
        sb.appendCodePoint(b << 16 | 0xfffe)
        sb.appendCodePoint(b << 16 | 0xffff)
      }
      Textify("foo" + sb.toString + "bar") must beEqualTo("foobar")
    }

    b(Charsets.UTF_8, "κόσμε".getBytes(Charsets.UTF_8), "κόσμε",
      "parse valid UTF-8")
    b(Charsets.UTF_8, Array[Int](0xe0, 0x80, 0xaf).map(_.toByte), "���",
      "replace invalid UTF-8 characters")
    b(Charsets.UTF_8, Array[Int](0xed, 0xbe, 0x80).map(_.toByte), "���",
      "replace UTF-8-encoded UTF-16 surrogates")
    b(Charsets.UTF_8, Array[Int](0xc0, 0x80).map(_.toByte), "��",
      "refuse to understand modified UTF-8's 0xc0 0x80 null byte encoding")
  }
}
