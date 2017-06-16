package models.export.format

import akka.stream.scaladsl.{Sink,Source}
import java.io.ByteArrayOutputStream
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}

import models.export.rows.Rows
import test.helpers.InAppSpecification // for materializer

class XlsxFormatSpec extends InAppSpecification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val rows = new Rows(Array(), Source.empty)
    lazy val bytes: Array[Byte] = await(XlsxFormat.byteSource(rows).runWith(Sink.seq)).reduce(_ ++ _).toArray
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
