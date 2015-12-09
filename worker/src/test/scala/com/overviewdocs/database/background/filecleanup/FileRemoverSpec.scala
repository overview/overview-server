package com.overviewdocs.background.filecleanup

import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.tables.Files
import com.overviewdocs.test.DbSpecification

class FileRemoverSpec extends DbSpecification with Mockito {
  "FileRemover" should {
    trait BaseScope extends DbScope {
      val mockBlobStorage = smartMock[BlobStorage]
      val mockPageRemover = smartMock[PageRemover]

      mockBlobStorage.delete(any) returns Future.successful(())
      mockBlobStorage.deleteMany(any) returns Future.successful(())
      mockPageRemover.removeFilePages(any) returns Future.successful(())

      val subject = new FileRemover {
        override val blobStorage = mockBlobStorage
        override val pageRemover = mockPageRemover
      }
    }

    "remove pages" in new BaseScope {
      val file = factory.file(referenceCount=0, contentsLocation="loc:1", viewLocation="loc:1")
      await(subject.deleteFile(file.id))
      there was one(mockPageRemover).removeFilePages(file.id)
    }

    "delete file content" in new BaseScope {
      val file = factory.file(referenceCount=0, contentsLocation="loc:1", viewLocation="loc:1")
      await(subject.deleteFile(file.id))
      there was one(mockBlobStorage).delete("loc:1")
    }

    "delete content and view if different" in new BaseScope {
      val file = factory.file(referenceCount=0, contentsLocation="loc:1", viewLocation="loc:2")
      await(subject.deleteFile(file.id))
      there was one(mockBlobStorage).deleteMany(Seq("loc:1", "loc:2"))
    }

    "delete file" in new BaseScope {
      val file = factory.file(referenceCount=0)
      await(subject.deleteFile(file.id))

      import database.api._
      blockingDatabase.option(Files.filter(_.id === file.id)) must beNone
    }
  }
}
