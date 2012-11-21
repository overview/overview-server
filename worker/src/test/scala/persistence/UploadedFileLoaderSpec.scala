package persistence

import helpers.DbSpecification
import testutil.DbSetup._

class UploadedFileLoaderSpec extends DbSpecification {

  step(setupDb)
  
  "UploadedFileLoader" should {
    
    "load uploaded file values" in new DbTestContext {
      val oid = 100l
      val size = 1999l
      val uploadedFileId = insertUploadedFile(oid, "content-disposition", "content-type", size)
      
      UploadedFileLoader.load(uploadedFileId) must be equalTo(UploadedFile(oid, size))
    }
  }
  
  step(shutdownDb)
}