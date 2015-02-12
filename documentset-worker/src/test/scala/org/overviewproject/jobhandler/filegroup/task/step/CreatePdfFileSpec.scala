package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.specs2.mock.Mockito
import org.overviewproject.blobstorage.BlobStorage
import scala.concurrent.Future
import java.io.InputStream
import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.models.File
import org.overviewproject.models.tables.Files

class CreatePdfFileSpec extends SlickSpecification with Mockito {

  "CreatePdfFile" should {

    "copy upload contents to BlobStorage" in new UploadScope {
      await(createFile.execute)
      
      there was one(blobStorage).create(BlobBucketId.FileContents, loInputStream, uploadSize)
    }

    "create a file with PDF content" in new UploadScope {
      await(createFile.execute)
      
      val file = Files.firstOption
      
      file.map(f => (f.contentsLocation, f.contentsSize, f.viewLocation, f.viewSize)) must 
        beSome((location, uploadSize, location, uploadSize))
    }

    "return the next step" in new UploadScope {
      val r = await(createFile.execute)
      
      r must be equalTo(NextStep)
    }
    
    "return failure on error" in new FailingCreationScope {
      createFile.execute must throwA[Exception].await
    }
    

    trait UploadScope extends DbScope {
      val fileGroup = factory.fileGroup()
      val uploadSize = 1000l
      val upload = factory.groupedFileUpload(fileGroupId = fileGroup.id, size = uploadSize)
      val location = "blob location"
      
      val loInputStream = smartMock[InputStream]
      val blobStorage = smartMock[BlobStorage]
      blobStorage.create(any, any, any) returns createResult
      
      val createFile = new TestCreatePdfFile(upload.id, blobStorage, loInputStream)
      
      def createResult: Future[String] = Future.successful(location)
    }
    
    
    trait FailingCreationScope extends UploadScope {
      override def createResult = Future.failed(new Exception) 
    }
  }

  case object NextStep extends TaskStep {
    override def execute = Future.successful(this)
  }

  class TestCreatePdfFile(val uploadedFileId: Long, val blobStorage: BlobStorage, loInputStream: InputStream)
  (implicit val session: Session) extends CreatePdfFile with SlickClientInSession {
    
    override protected def nextStep(file: File) = NextStep
    override protected def largeObjectInputStream(oid: Long) = loInputStream
  }
}