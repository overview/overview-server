package org.overviewproject.background.filegroupcleanup

import org.overviewproject.database.Slick.simple._
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.specs2.mock.Mockito

class GroupedFileUploadRemoverSpec extends SlickSpecification with Mockito {

  "GroupedFileUploadRemover" should {

    "delete content" in new GroupedFileUploadScope {
      await(remover.removeUploadsFromFileGroup(fileGroup.id))
      
      there was one(mockBlobStorage).deleteMany(contentLocations)
    }

    "delete GroupedFileUpload" in {
      todo
    }
  }

  trait GroupedFileUploadScope extends DbScope {
    val numberOfUploads = 10
    val contentOids: Seq[Long] = Seq.range(1, numberOfUploads)
    val contentLocations = contentOids.map(coid => s"pglo:$coid")
    
    val fileGroup = factory.fileGroup(deleted = true)
    val uploads = contentOids.map(coid =>
      factory.groupedFileUpload(fileGroupId = fileGroup.id, contentsOid = coid))
      
    val mockBlobStorage = smartMock[BlobStorage]
    val remover = new TestGroupedFileUploadRemover(mockBlobStorage)
  }

  class TestGroupedFileUploadRemover(storage: BlobStorage)(implicit val session: Session)
    extends GroupedFileUploadRemover with SlickClientInSession {
    override protected val blobStorage = storage
  }

}