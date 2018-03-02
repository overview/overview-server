package com.overviewdocs.background.filegroupcleanup

import org.specs2.mock.Mockito
import scala.concurrent.{ Await, Future, Promise, TimeoutException }
import scala.concurrent.duration._

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.tables.GroupedFileUploads
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.util.AwaitMethod

class GroupedFileUploadRemoverSpec extends DbSpecification with Mockito with AwaitMethod {

  "GroupedFileUploadRemover" should {

    "delete content" in new GroupedFileUploadScope {
      pgUnlinkResult.success(())
      await(remover.removeFileGroupUploads(fileGroup.id))

      remover.lastDeletedOids must containTheSameElementsAs(contentOids)
    }

    "complete when content deletion completes" in new GroupedFileUploadScope {
      val r = remover.removeFileGroupUploads(fileGroup.id)

      r.isCompleted must beFalse

      pgUnlinkResult.success(())

      await(r) must beEqualTo(())
    }

    "delete GroupedFileUpload" in new GroupedFileUploadScope {
      pgUnlinkResult.success(())
      await(remover.removeFileGroupUploads(fileGroup.id))
      
      import database.api._
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

    val pgUnlinkResult = Promise[Unit]()
    class TestGroupedFileUploadRemover extends GroupedFileUploadRemover {
      var lastDeletedOids: Seq[Long] = Seq()
      override protected def deleteLargeObjectsByOids(oids: Seq[Long]): Future[Unit] = {
        lastDeletedOids = oids
        pgUnlinkResult.future
      }
    }

    val remover = new TestGroupedFileUploadRemover
  }
}
