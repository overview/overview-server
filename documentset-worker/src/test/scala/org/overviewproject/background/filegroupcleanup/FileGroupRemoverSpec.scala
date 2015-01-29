package org.overviewproject.background.filegroupcleanup

import scala.concurrent.Promise
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.FileGroups
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.specs2.mock.Mockito

class FileGroupRemoverSpec extends SlickSpecification with Mockito {

  "FileGroupRemover" should {

    "delete GroupedFileUploads" in new FileGroupScope {
      uploadsRemoved.success(())
      await(fileGroupRemover.remove(fileGroup.id))
      
      there was one(groupedFileUploadRemover).removeUploadsFromFileGroup(fileGroup.id)
    }

    "delete FileGroup after uploads are deleted" in new FileGroupScope {
      val r = fileGroupRemover.remove(fileGroup.id)
      
      FileGroups.firstOption must beSome
      
      uploadsRemoved.success(())
      await(r)
      
      FileGroups.firstOption must beNone
    }

  }

  trait FileGroupScope extends DbScope {
    val fileGroup = factory.fileGroup(deleted = true)

    val groupedFileUploadRemover = smartMock[GroupedFileUploadRemover]
    val mockBlobStorage = smartMock[BlobStorage]
    
    val fileGroupRemover = new TestFileGroupRemover(groupedFileUploadRemover, mockBlobStorage)
    
    val uploadsRemoved = Promise[Unit]()
    groupedFileUploadRemover.removeUploadsFromFileGroup(any) returns uploadsRemoved.future
  }

  class TestFileGroupRemover(remover: GroupedFileUploadRemover, storage: BlobStorage)(implicit val session: Session) extends FileGroupRemover with SlickClientInSession {
    override protected val groupedFileUploadRemover = remover
    override protected val blobStorage = storage
  }

}