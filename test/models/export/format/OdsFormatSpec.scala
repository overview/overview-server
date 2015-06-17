package models.export.format

import java.io.ByteArrayOutputStream
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}

import models.export.rows.Rows

class OdsFormatSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val rows = new Rows(Array(), Enumerator())

    lazy val bytes: Array[Byte] = {
      val stream = new ByteArrayOutputStream
      await(OdsFormat.writeContentsToOutputStream(rows, stream))
      stream.toByteArray
    }
  }

  "OdsFormat" should {
    "have the correct content-type" in new BaseScope {
      OdsFormat.contentType must beEqualTo("""application/vnd.oasis.opendocument.spreadsheet""")
    }

    "have the correct magic number" in new BaseScope {
      bytes.slice(0, 2) must beEqualTo("PK".getBytes)
    }
  }
}
