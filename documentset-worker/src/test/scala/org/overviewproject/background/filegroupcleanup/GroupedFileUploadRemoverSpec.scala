package org.overviewproject.background.filegroupcleanup

import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import scala.concurrent.{ Await, Promise, TimeoutException }
import scala.concurrent.duration._

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.test.DbSpecification

class GroupedFileUploadRemoverSpec extends DbSpecification with Mockito with NoTimeConversions {

  "GroupedFileUploadRemover" should {

    "delete content" in new GroupedFileUploadScope {
      deleteMany.success(())
      await(remover.removeFileGroupUploads(fileGroup.id))

      there was one(mockBlobStorage).deleteMany(argThat(beLike[Seq[String]] { case (actual: Seq[String]) =>
        actual must containTheSameElementsAs(contentLocations)
      }))
    }

    "complete when content deletion completes" in new GroupedFileUploadScope {
      val r = remover.removeFileGroupUploads(fileGroup.id)

      Await.result(r, 10 millis) must throwA[TimeoutException]
      r.isCompleted must beFalse

      deleteMany.success(())

      await(r)
      r.isCompleted must beTrue
    }

    "delete GroupedFileUpload" in new GroupedFileUploadScope {
      deleteMany.success(())
      await(remover.removeFileGroupUploads(fileGroup.id))
      
      import databaseApi._
      blockingDatabase.length(GroupedFileUploads) must beEqualTo(0)
    }
  }

  trait GroupedFileUploadScope extends DbScope {
    val numberOfUploads = 3
    val contentOids: Seq[Long] = Seq.range(1, numberOfUploads)
    val contentLocations = contentOids.map(coid => s"pglo:$coid")

    val fileGroup = factory.fileGroup(deleted = true)
    val uploads = contentOids.map(coid =>
      factory.groupedFileUpload(fileGroupId = fileGroup.id, contentsOid = coid))

    val deleteMany = Promise[Unit]()
    
    val mockBlobStorage = smartMock[BlobStorage]
    mockBlobStorage.deleteMany(any) returns deleteMany.future
    
    val remover = new TestGroupedFileUploadRemover(mockBlobStorage)
  }

  class TestGroupedFileUploadRemover(storage: BlobStorage)
    extends GroupedFileUploadRemover with org.overviewproject.database.DatabaseProvider {
    override protected val blobStorage = storage
  }

}
