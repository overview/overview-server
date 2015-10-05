package models.export.format

import java.io.{ByteArrayOutputStream,FilterOutputStream,OutputStream}
import org.specs2.mutable.Specification
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}

import models.export.rows.Rows

class WriteBasedFormatSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  "WriteBasedFormat" should {
    "work" in {
      class AFormat extends Format with WriteBasedFormat[FilterOutputStream] {
        override val contentType = "foo/bar"
        override protected def createContext(sink: OutputStream) = new FilterOutputStream(sink)
        override protected def writeBegin(s: FilterOutputStream) = s.write("begin\n".getBytes)
        override protected def writeHeaders(x: Array[String], s: FilterOutputStream) = s.write(("Headers " + x.mkString(",") + "\n").getBytes)
        override protected def writeRow(x: Array[String], s: FilterOutputStream) = s.write(("Row " + x.mkString(",") + "\n").getBytes)
        override protected def writeEnd(x: FilterOutputStream) = x.write("end".getBytes)
      }
      val rows = new Rows(Array("a","b"), Enumerator(Array("c", "d"), Array("e", "f")))
      val format = new AFormat
      val chunks: Seq[Array[Byte]] = await(format.bytes(rows).run(Iteratee.getChunks))

      chunks.map((b) => new String(b, "utf-8")) must beEqualTo(Seq("begin\n", "Headers a,b\n", "Row c,d\n", "Row e,f\n", "end"))
    }
  }
}
