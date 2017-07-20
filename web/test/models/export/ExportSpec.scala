package models.export

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator

import models.export.rows.Rows
import models.export.format.Format

class ExportSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val rows = Rows(Array(), Source.empty)
    val format = smartMock[Format]
    val export = new Export(rows, format)
  }

  "Export" should {
    "have the correct contentType" in new BaseScope {
      format.contentType returns "application/foobar"
      export.contentType must beEqualTo("application/foobar")
    }

    "pass Rows to Format" in new BaseScope {
      format.byteSource(any) returns Source.empty[ByteString]
      export.byteSource
      there was one(format).byteSource(rows)
    }
  }
}
