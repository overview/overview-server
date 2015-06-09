package org.overviewproject.csv

import java.io.Reader
import java.nio.charset.Charset

import org.overviewproject.database.LargeObject
import org.overviewproject.test.DbSpecification

class UploadReaderSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    val data: Array[Byte] = new Array[Byte](0)
    val encodingStringOption: Option[String] = None
    val buffer = new Array[Char](1024)

    val loManager = blockingDatabase.largeObjectManager

    import databaseApi._

    lazy val loid = blockingDatabase.run((for {
      oid <- loManager.create
      lo <- loManager.open(oid, LargeObject.Mode.Write)
      _ <- lo.write(data)
    } yield oid).transactionally)

    lazy val uploadReader = new UploadReader(loid, encodingStringOption, blockingDatabase)
    lazy val reader = uploadReader.reader
  }

  "Read from a LargeObject" in new BaseScope {
    override val data = "foobar".getBytes("utf-8")
    reader.read(buffer) must beEqualTo(6) // side-effect here
    buffer.take(6) must beEqualTo("foobar".toCharArray)
    reader.read(buffer) must beEqualTo(-1)
  }
    
  "return bytesRead" in new BaseScope {
    override val data = "foobar".getBytes("utf-8")
    uploadReader.bytesRead must beEqualTo(0)
    reader.read(buffer)
    uploadReader.bytesRead must beEqualTo(6)
  }

  "read non-default encoding" in new BaseScope {
    override val data = "Àä€Š".getBytes("windows-1252")
    override val encodingStringOption = Some("windows-1252")
    uploadReader.charset.name must beEqualTo("windows-1252")
    reader.read(buffer)
    buffer.take(4) must beEqualTo("Àä€Š".toCharArray)
  }

  "default to UTF-8 if specified encoding is not valid" in new BaseScope {
    override val data = "ἀπὸ τὸ Ἄξιον ἐστί".getBytes("utf-8")
    override val encodingStringOption = Some("some-nonexistent-encoding")
    uploadReader.charset.name must beEqualTo("UTF-8-backport") // should be "utf-8"
    reader.read(buffer)
    buffer.take(17) must beEqualTo("ἀπὸ τὸ Ἄξιον ἐστί".toCharArray)
  }

  "insert replacement character for invalid input" in new BaseScope {
    override val data = new Array[Byte](2)
    data(0) = 0xff.toByte
    data(1) = 0xfe.toByte
    reader.read(buffer)
    buffer.take(2) must beEqualTo("��".toCharArray)
  }
    
  "insert replacement character for unmappable input" in new BaseScope {
    override val data = "åäö".getBytes("iso-8859-15")
    reader.read(buffer)
    buffer.take(3) must beEqualTo("���".toCharArray)
  }
}
