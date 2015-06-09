package org.overviewproject.background.filegroupcleanup

import org.specs2.mock.Mockito
import scala.concurrent.Promise

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.FileGroups
import org.overviewproject.test.DbSpecification

class FileGroupRemoverSpec extends DbSpecification with Mockito {
  "FileGroupRemover" should {

    "delete GroupedFileUploads" in new FileGroupScope {
      uploadsRemoved.success(())
      await(fileGroupRemover.remove(fileGroup.id))
      
      there was one(groupedFileUploadRemover).removeFileGroupUploads(fileGroup.id)
    }

    "delete FileGroup after uploads are deleted" in new FileGroupScope {
      val r = fileGroupRemover.remove(fileGroup.id)

      import databaseApi._
      blockingDatabase.length(FileGroups) must beEqualTo(1)

      uploadsRemoved.success(())
      await(r)

      blockingDatabase.length(FileGroups) must beEqualTo(0)
    }
  }

  trait FileGroupScope extends DbScope {
    val fileGroup = factory.fileGroup(deleted = true)

    val groupedFileUploadRemover = smartMock[GroupedFileUploadRemover]
    val mockBlobStorage = smartMock[BlobStorage]
    
    val fileGroupRemover = new TestFileGroupRemover(groupedFileUploadRemover, mockBlobStorage)
    
    val uploadsRemoved = Promise[Unit]()
    groupedFileUploadRemover.removeFileGroupUploads(any) returns uploadsRemoved.future
  }

  class TestFileGroupRemover(remover: GroupedFileUploadRemover, storage: BlobStorage)
      extends FileGroupRemover
      with org.overviewproject.database.DatabaseProvider {
    override protected val groupedFileUploadRemover = remover
    override protected val blobStorage = storage
  }

}
