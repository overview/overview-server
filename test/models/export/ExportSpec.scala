package models.export

import java.io.{FileInputStream,OutputStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.test.{FutureAwaits,DefaultAwaitTimeout}
import scala.concurrent.Future

import models.export.rows.Rows
import models.export.format.Format
import com.overviewdocs.util.TempFile

class ExportSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  def createFileInputStreamWithBytes(bytes: Array[Byte]) = {
    val tempFile = new TempFile()
    tempFile.outputStream.write(bytes)
    tempFile.outputStream.close()
    tempFile.inputStream
  }

  trait BaseScope extends Scope {
    val rows = Rows(Array(), Enumerator())
    val fileInputStream = createFileInputStreamWithBytes("foobar".getBytes("utf-8"))

    val format = new Format {
      override val contentType = "application/foobar"
      override def writeContentsToOutputStream(x: Rows, y: OutputStream) = ???
      override def getContentsAsInputStream(x: Rows) = {
        if (x != rows) throw new IllegalArgumentException
        Future.successful(fileInputStream)
      }
    }

    val export = new Export(rows, format)
  }

  "Export" should {
    "have the correct contentType" in new BaseScope {
      export.contentType must beEqualTo("application/foobar")
    }

    "return a FileInputStream" in new BaseScope {
      val is = await(export.futureFileInputStream)
      is must beAnInstanceOf[FileInputStream]
      val bytes = new Array[Byte](6)
      is.read(bytes)
      bytes must beEqualTo("foobar".getBytes("utf-8"))
    }
  }
}
