package models.export.format

import java.io.{FileInputStream,OutputStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}
import scala.concurrent.Future

import models.export.rows.Rows

class FormatSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val exampleRows = Rows(Array(), Enumerator())

    object TestFormat extends Format {
      override val contentType : String = """text/csv; charset="utf-8""""

      override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream): Future[Unit] = {
        outputStream.write("foobar".getBytes("utf-8"))
        Future.successful(())
      }
    }
  }

  "Format" should {
    "give the correct content-type header" in new BaseScope {
      TestFormat.contentType must beEqualTo("""text/csv; charset="utf-8"""")
    }

    "return output as a FileInputStream" in new BaseScope {
      val inputStream: FileInputStream = await(TestFormat.getContentsAsInputStream(exampleRows))
      val bytes = new Array[Byte](6)
      inputStream.read(bytes)
      new String(bytes, "utf-8") must beEqualTo("foobar")
    }
  }
}
