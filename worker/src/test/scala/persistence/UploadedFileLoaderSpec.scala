package persistence

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._

class UploadedFileLoaderSpec extends DbSpecification {

  step(setupDb)
  
  "UploadedFileLoader" should {
    
    "load uploaded file values" in new DbTestContext {
      val oid = 100l
      val size = 1999l
      val contentType =  "application/octet-stream"
        
      val uploadedFileId = insertUploadedFile(oid, "content-disposition", contentType, size)
      
      UploadedFileLoader.load(uploadedFileId) must be equalTo(EncodedUploadFile(oid, contentType, size))
    }
  }
  
  step(shutdownDb)
  
  "UploadedFile" should {
    
    "return None if no encoding can be found" in {
      val uploadedFile = EncodedUploadFile(0l, "application/octet-stream", 100)
      uploadedFile.encoding must beNone
    }
    
    "return specified encoding" in {
      val encoding = "someEncoding"
      val uploadedFile = EncodedUploadFile(0l, "application/octet-stream; charset=" + encoding, 100)
      uploadedFile.encoding must beSome.like { case c => c must be equalTo(encoding) }
    }
  }
}