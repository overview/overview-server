package models.export

import java.io.{ FileInputStream, OutputStream }
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.util.TempFile

class ExportSpec extends Specification {
  trait BaseScope extends Scope {
    val contents : Array[Byte] = "foobar".getBytes

    val export = new Export {
      def contentTypeHeader = "text/csv; charset=\"utf-8\""

      def exportTo(outputStream: OutputStream) = {
        outputStream.write(contents)
      }
    }
  }

  "Export" should {
    "create a FileInputStream in exportToInputStream" in new BaseScope {
      val inputStream : FileInputStream = export.exportToInputStream
      val bytes = new Array[Byte](1024)
      val length = inputStream.read(bytes)
      new String(bytes, 0, length) must beEqualTo(new String(contents))
    }
  }
}
