package persistence

import helpers.DbSpecification
import testutil.DbSetup._

class UploadedFileLoaderSpec extends DbSpecification {

  step(setupDb)
  
  "UploadedFileLoader" should {
    
    "load uploaded file values" in new DbTestContext {
      val oid = 100l
      val size = 1999l
      val contentType =  "application/octet-stream"
        
      val uploadedFileId = insertUploadedFile(oid, "content-disposition", contentType, size)
      
      UploadedFileLoader.load(uploadedFileId) must be equalTo(UploadedFile(oid, contentType, size))
    }
  }
  
  step(shutdownDb)
  
 /* "UploadedFile" should {
    
    "return None if no encoding can be found" in {
      val uploadedFile = UploadedFile(0l, "application/octet-stream", 100)
    }
  }*/
}