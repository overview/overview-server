package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.UploadedFile

class EncodedUploadFileSpec extends DbSpecification {

  step(setupDb)

  "EncodedUploadedFile" should {

    trait UploadedFileContext extends DbTestContext {
      val size = 1999l
      val contentType = "application/octet-stream"

      var uploadedFile: UploadedFile = _

      override def setupWithDb = {
        uploadedFile = Schema.uploadedFiles.insert(UploadedFile("content-disposition", contentType, size))
      }
    }

    "load uploaded file values" in new UploadedFileContext {
      val encodedUploadedFile = EncodedUploadFile.load(uploadedFile.id)
      encodedUploadedFile.contentType must be equalTo contentType
      encodedUploadedFile.size must be equalTo size
    }

  }

  step(shutdownDb)

  case class TestUploadFile(contentType: String) extends EncodedUploadFile {
    val size: Long = 100
  }

  "EncodedUploadedFile" should {

    "return None if no encoding can be found" in {
      val uploadedFile = TestUploadFile("application/octet-stream")
      uploadedFile.encoding must beNone
    }

    "return specified encoding" in {
      val encoding = "someEncoding"
      val uploadedFile = TestUploadFile("application/octet-stream; charset=" + encoding)
      uploadedFile.encoding must beSome(encoding)
    }
  }
}
