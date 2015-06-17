package models.export.format

import java.io.ByteArrayOutputStream
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}

import models.export.rows.Rows

class XlsxFormatSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val rows = new Rows(Array(), Enumerator())

    lazy val bytes: Array[Byte] = {
      val stream = new ByteArrayOutputStream
      await(XlsxFormat.writeContentsToOutputStream(rows, stream))
      stream.toByteArray
    }
  }

  "XlsxFormat" should {
    "have the correct content-type" in new BaseScope {
      XlsxFormat.contentType must beEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    }

    "have the correct magic number" in new BaseScope {
      // Check for "PK"
      bytes(0) must beEqualTo(0x50.toByte)
      bytes(1) must beEqualTo(0x4b.toByte)
    }
  }
}
