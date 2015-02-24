package org.overviewproject.background.filegroupcleanup

import org.specs2.mock.Mockito
import scala.concurrent.Promise
import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.FileGroups
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }

class FileGroupRemoverSpec extends SlickSpecification with Mockito {

  "FileGroupRemover" should {

    "delete GroupedFileUploads" in new FileGroupScope {
      uploadsRemoved.success(())
      await(fileGroupRemover.remove(fileGroup.id))
      
      there was one(groupedFileUploadRemover).removeFileGroupUploads(fileGroup.id)
    }

    "delete FileGroup after uploads are deleted" in new FileGroupScope {
      val r = fileGroupRemover.remove(fileGroup.id)

      import org.overviewproject.database.Slick.simple._
      
      FileGroups.firstOption(session) must beSome
      
      uploadsRemoved.success(())
      await(r)
      
      FileGroups.firstOption(session) must beNone
    }

  }

  trait FileGroupScope extends DbScope {
    val fileGroup = factory.fileGroup(deleted = true)

    val groupedFileUploadRemover = smartMock[GroupedFileUploadRemover]
    val mockBlobStorage = smartMock[BlobStorage]
    
    val fileGroupRemover = new TestFileGroupRemover(groupedFileUploadRemover, mockBlobStorage)(session)
    
    val uploadsRemoved = Promise[Unit]()
    groupedFileUploadRemover.removeFileGroupUploads(any) returns uploadsRemoved.future
  }

  class TestFileGroupRemover(remover: GroupedFileUploadRemover, storage: BlobStorage)(implicit val session: Session) extends FileGroupRemover with SlickClientInSession {
    override protected val groupedFileUploadRemover = remover
    override protected val blobStorage = storage
  }

}
