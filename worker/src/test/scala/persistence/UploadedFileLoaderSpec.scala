package persistence

import helpers.DbSpecification
import testutil.DbSetup._

class UploadedFileLoaderSpec extends DbSpecification {

  step(setupDb)
  
  "UploadedFileLoader" should {
    
    "load contentsOid value" in new DbTestContext {
      val oid = 100l
      val uploadedFileId = insertUploadedFile(oid, "content-disposition", "content-type")
      
      UploadedFileLoader.load(uploadedFileId) must be equalTo(oid)
    }
  }
  
  step(shutdownDb)
}