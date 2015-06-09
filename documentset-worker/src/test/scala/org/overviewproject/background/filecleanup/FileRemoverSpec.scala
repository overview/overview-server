package org.overviewproject.background.filecleanup

import java.util.concurrent.TimeoutException
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.Files
import org.overviewproject.test.DbSpecification

class FileRemoverSpec extends DbSpecification with Mockito with NoTimeConversions {

  "FileRemover" should {

    "remove pages" in new FileScope {
      deleteFile

      there was one(pageRemover).removeFilePages(file.id)
    }

    "delete file content" in new FileScope {
      deleteFile

      there was one(blobStorage).delete(contentsLoc)
    }

    "delete content and view if different" in new FileWithViewScope {
      deleteFile

      there was one(blobStorage).deleteMany(Seq(contentsLoc, viewLoc))
    }

    "delete file" in new FileScope {
      deleteFile

      import databaseApi._
      blockingDatabase.option(Files.filter(_.id === file.id)) must beNone
    }
  }

  trait FileScope extends DbScope {
    def contentsLoc = "contents:location"
    val file = createFile

    val blobStorage = smartMock[BlobStorage]
    val pageRemover = smartMock[PageRemover]

    val blobDelete = Promise[Unit]()
    blobStorage.delete(any) returns blobDelete.future

    pageRemover.removeFilePages(file.id) returns Future.successful(())

    val fileRemover = new TestFileRemover(blobStorage, pageRemover)

    protected def createFile = factory.file(referenceCount = 0,
        contentsLocation = contentsLoc, viewLocation = contentsLoc)

    protected def deleteFile = {
      val r = fileRemover.deleteFile(file.id)
      Await.result(r, 10 millis) must throwA[TimeoutException]

      completeDelete
      await(r)
    }

    protected def completeDelete = blobDelete.success(())
  }

  trait FileWithViewScope extends FileScope {
    def viewLoc = "view:location"
    override def createFile = factory.file(referenceCount = 0,
      contentsLocation = contentsLoc, viewLocation = viewLoc)

    val blobDeleteMany = Promise[Unit]()

    blobStorage.deleteMany(any) returns blobDeleteMany.future

    override protected def completeDelete = blobDeleteMany.success(())

  }

  class TestFileRemover(val blobStorage: BlobStorage, val pageRemover: PageRemover)
    extends FileRemover with org.overviewproject.database.DatabaseProvider
}
