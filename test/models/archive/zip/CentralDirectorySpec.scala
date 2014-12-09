package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import java.util.Calendar
import scala.concurrent.Future

import models.archive.DosDate

class CentralDirectorySpec extends Specification  {

  "CentralDirectory" should {

    "compute size" in  new CentralDirectoryContext {
      centralDirectory.size must be equalTo directorySize
    }

    "compute offset" in new CentralDirectoryContext {
      centralDirectory.offset must be equalTo numberOfEntries * entrySize
    }

    "create bytes from local file headers" in new CentralDirectoryContext {
      val output = centralDirectory.bytes

      output.size must be equalTo centralDirectory.size
    }
  }

  trait CentralDirectoryContext extends Scope with Mockito {
    val numberOfEntries = 10
    val entrySize = 128
    val data = Array.fill[Byte](entrySize)(0xff.toByte)

    val fileNames = Seq.tabulate(numberOfEntries)(n => f"file$n%02d")

    val dummyOffset = 100

    val entries = fileNames.map { s => 
      val entry = smartMock[LocalFileEntry]
      entry.fileName returns s
      entry.crcFuture returns Future.successful(123)
      entry.size returns entrySize
      entry.offset returns dummyOffset
      entry.timeStamp returns DosDate(Calendar.getInstance())

      entry
    }

    val directorySize = fileNames.map(s => 46 + s.length).sum

    val centralDirectory = new CentralDirectory(entries)
  }
}
