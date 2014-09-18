package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import models.archive.CRCInputStream
import java.nio.charset.StandardCharsets
import org.specs2.specification.Scope

class Zip64LocalFileEntrySpec extends Specification with Mockito {

  "Zip64LocalFileEntry" should {

    "return size" in new FileEntryContext {
      val fileName = "12334567890"

      val entry = new Zip64LocalFileEntry(fileName, fileSize, data)

      entry.size must be equalTo (entrySize(fileName.size))
    }

    "count UTF-8 filename size correctly" in new FileEntryContext {
      val utf8Bytes = Array(0xd0, 0xad, 0xd0, 0x9c, 0xd0, 0x98, 0xd0, 0x9b, 0xd0, 0x98).map(_.toByte)
      val utf8FileName = new String(utf8Bytes, StandardCharsets.UTF_8)

      val entry = new Zip64LocalFileEntry(utf8FileName, fileSize, data)

      entry.size must be equalTo (entrySize(utf8FileName.size * 2))

    }
   
    trait FileEntryContext extends Scope {
      val localFileHeaderSize = 30
      val extraFieldSize = 32
      val dataDescriptorSize = 24

      val fileSize = 100
      val data = mock[CRCInputStream]
      
      def entrySize(fileNameSize: Int) = localFileHeaderSize + extraFieldSize + dataDescriptorSize + fileSize + fileNameSize

    }
  }

}