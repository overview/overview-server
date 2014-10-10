package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.InputStream
import java.io.ByteArrayInputStream
import models.archive.ArchiveEntry

class LocalFileEntrySpec extends Specification {
  
  "LocalFileEntry" should {
    
    "start with uninitialized crc" in new LocalFileContext {
      val localFileEntry = new LocalFileEntry(archiveEntry)
      
      localFileEntry.crc must beNone
    }
    
    "set crc32 after generating stream" in {
      todo
    }
    
    "write values to stream" in {
      todo
    }
  
    
    
  }

}


trait  LocalFileContext extends Scope {
  
  val numberOfBytes = 52
  val data = Array.range(1, numberOfBytes).map(_.toByte)
  def stream(): InputStream = new ByteArrayInputStream(data)
  
  val archiveEntry = ArchiveEntry(numberOfBytes, "filename", stream)
  
}