package org.overviewproject.background.filegroupcleanup

import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import scala.concurrent.{ Await, Promise, TimeoutException }
import scala.concurrent.duration._
import slick.jdbc.JdbcBackend.Session

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }

class GroupedFileUploadRemoverSpec extends SlickSpecification with Mockito with NoTimeConversions {

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
      
      import org.overviewproject.database.Slick.simple._

      GroupedFileUploads.firstOption(session) must beNone
    }
  }

  trait GroupedFileUploadScope extends DbScope {
    val numberOfUploads = 10
    val contentOids: Seq[Long] = Seq.range(1, numberOfUploads)
    val contentLocations = contentOids.map(coid => s"pglo:$coid")

    val fileGroup = factory.fileGroup(deleted = true)
    val uploads = contentOids.map(coid =>
      factory.groupedFileUpload(fileGroupId = fileGroup.id, contentsOid = coid))

    val deleteMany = Promise[Unit]()
    
    val mockBlobStorage = smartMock[BlobStorage]
    mockBlobStorage.deleteMany(any) returns deleteMany.future
    
    val remover = new TestGroupedFileUploadRemover(mockBlobStorage)(session)
  }

  class TestGroupedFileUploadRemover(storage: BlobStorage)(implicit val session: Session)
    extends GroupedFileUploadRemover with SlickClientInSession {
    override protected val blobStorage = storage
  }

}
