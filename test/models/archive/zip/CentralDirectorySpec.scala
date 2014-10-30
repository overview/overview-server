package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.StreamReader
import java.io.ByteArrayInputStream
import models.archive.DosDate
import java.util.Calendar

class CentralDirectorySpec extends Specification  {

  "CentralDirectory" should {
    
    "compute size" in  new CentralDirectoryContext {
      centralDirectory.size must be equalTo directorySize
    }
    
    "compute offset" in new CentralDirectoryContext {
      centralDirectory.offset must be equalTo numberOfEntries * entrySize
    }
    
    "create stream from local file headers" in new CentralDirectoryContext {
      val output = readStream(centralDirectory.stream)
      
      output.size must be equalTo centralDirectory.size
    }
  }
  
}


trait CentralDirectoryContext extends Scope with Mockito with StreamReader {
   val numberOfEntries = 10
   val entrySize = 128
   val data = Array.fill[Byte](entrySize)(0xff.toByte)
   
   val fileNames = Seq.tabulate(numberOfEntries)(n => f"file$n%02d")
   
   val dummyOffset = 100
   
   val entries = fileNames.map { s => 
     val entry = smartMock[LocalFileEntry]
     entry.fileName returns s
     entry.size returns entrySize
     entry.offset returns dummyOffset
     entry.stream returns new ByteArrayInputStream(data)
     entry.timeStamp returns DosDate(Calendar.getInstance())
     
     entry
   }

   val directorySize = fileNames.map(s => 46 + s.length).sum
   
   val centralDirectory = new CentralDirectory(entries)
}