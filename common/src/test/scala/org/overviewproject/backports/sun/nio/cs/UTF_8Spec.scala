package org.overviewproject.backports.sun.nio.cs

import com.google.common.base.Charsets

import org.specs2.mutable.Specification

class UTF_8Spec extends Specification {
  "UTF_8" should {
    def b(rawBytes: Array[Byte], expected: String, message: String): Unit = {
      message in {
        val charset = new UTF_8()
        new String(rawBytes, charset) must beEqualTo(expected)
      }
    }

    b("κόσμε".getBytes(Charsets.UTF_8), "κόσμε",
      "parse valid UTF-8")
    b(Array[Int](0xe0, 0x80, 0xaf).map(_.toByte), "���",
      "replace invalid UTF-8 characters")
    b(Array[Int](0xed, 0xbe, 0x80, 0xed, 0xbe, 0x80).map(_.toByte), "��",
      "replace UTF-8-encoded UTF-16 surrogates")
    b(Array[Int](0xc0, 0x80).map(_.toByte), "��",
      "refuse to understand modified UTF-8's 0xc0 0x80 null byte encoding")
  }
}
