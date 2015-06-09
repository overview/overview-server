package org.overviewproject.database

import org.specs2.mock.Mockito
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.DocumentSetCreationJobType._
import org.overviewproject.models.tables.DocumentSetCreationJobs
import org.overviewproject.test.DbSpecification

class DocumentSetCreationJobDeleterSpec extends DbSpecification with Mockito {

  "DocumentSetCreationJobDeleter" should {

    "delete by document set ID" in new BaseScope {
      val documentSet = factory.documentSet()
      val otherDocumentSet = factory.documentSet()
      val job1 = factory.documentSetCreationJob(documentSetId=documentSet.id)
      val job2 = factory.documentSetCreationJob(documentSetId=documentSet.id)
      val otherJob = factory.documentSetCreationJob(documentSetId=otherDocumentSet.id)

      await { deleter.deleteByDocumentSet(documentSet.id) }

      import databaseApi._
      blockingDatabase.length(DocumentSetCreationJobs.filter(_.documentSetId === documentSet.id)) must beEqualTo(0)
      blockingDatabase.length(DocumentSetCreationJobs.filter(_.documentSetId === otherDocumentSet.id)) must beEqualTo(1)
    }

    "delete by ID" in new BaseScope {
      val documentSet = factory.documentSet()
      val openJob = factory.documentSetCreationJob(documentSetId=documentSet.id)
      val cancelledJob = factory.documentSetCreationJob(documentSetId=documentSet.id, state=Cancelled)

      await { deleter.delete(cancelledJob.id) }

      import databaseApi._
      blockingDatabase.option(DocumentSetCreationJobs.filter(_.id === cancelledJob.id)) must beNone
      blockingDatabase.option(DocumentSetCreationJobs.filter(_.id === openJob.id)) must beSome(openJob)
    }

    "delete uploaded csv" in new BaseScope {
      val documentSet = factory.documentSet()
      val job = factory.documentSetCreationJob(documentSetId=documentSet.id, contentsOid=Some(1234L))

      await { deleter.deleteByDocumentSet(documentSet.id) }

      import databaseApi._
      there was one(mockBlobStorage).deleteMany(Seq(s"pglo:1234"))
    }
  }

  trait BaseScope extends DbScope {
    val mockBlobStorage = smartMock[BlobStorage]
    mockBlobStorage.deleteMany(any) returns Future.successful(())
    val deleter = new TestDocumentSetDeleter(mockBlobStorage)
  }

  class TestDocumentSetDeleter(override protected val blobStorage: BlobStorage)
    extends DocumentSetCreationJobDeleter with DatabaseProvider
}
