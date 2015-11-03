package controllers.backend

import java.time.Instant

import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJobState,DocumentSetCreationJobType,DocumentSetCreationJobImportJob,DocumentSetUser,FileGroupImportJob,ImportJob}

class DbImportJobBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbImportJobBackend {}
  }

  "indexByUser" should {
    trait IndexByUserScope extends BaseScope {
      def ret: Iterable[ImportJob] = await(backend.indexByUser("user@example.org"))
    }

    "return nothing when there are no jobs" in new IndexByUserScope {
      ret must beEqualTo(Iterable())
    }

    "return a DocumentSetCreationJob job" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable(DocumentSetCreationJobImportJob(documentSetCreationJob)))
    }

    "ignore a DocumentSetCreationJob that is not InProgress" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.Cancelled,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable())
    }

    "ignore a DocumentSetCreationJob of type Recluster" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.Recluster
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable())
    }

    "return a DocumentSet that the user only views (does not own)" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      ret must beEqualTo(Iterable())
    }

    "ignore a DocumentSet that belongs to a different user" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "user2@example.org")
      ret must beEqualTo(Iterable())
    }

    "return a FileGroup job" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable(FileGroupImportJob(fileGroup)))
    }

    "ignore a FileGroup that has no addToDocumentSetId" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(addToDocumentSetId=None)
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable())
    }

    "ignore a deleted FileGroup" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      factory.fileGroup(
        deleted=true,
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable())
    }
  }

  "indexByDocumentSet" should {
    trait IndexByDocumentSetScope extends BaseScope {
      val documentSet = factory.documentSet()
      def ret: Iterable[ImportJob] = await(backend.indexByDocumentSet(documentSet.id))
    }

    "return nothing when there are no jobs" in new IndexByDocumentSetScope {
      ret must beEqualTo(Iterable())
    }

    "return a DocumentSetCreationJob job" in new IndexByDocumentSetScope {
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      ret must beEqualTo(Iterable(DocumentSetCreationJobImportJob(documentSetCreationJob)))
    }

    "ignore a DocumentSetCreationJob that is not InProgress" in new IndexByDocumentSetScope {
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.Cancelled,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      ret must beEqualTo(Iterable())
    }

    "ignore a DocumentSetCreationJob of type Recluster" in new IndexByDocumentSetScope {
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.Recluster
      )
      ret must beEqualTo(Iterable())
    }

    "ignore a DocumentSetCreationJob of a different DocumentSet" in new IndexByDocumentSetScope {
      val documentSet2 = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet2.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      ret must beEqualTo(Iterable())
    }

    "return a FileGroup job" in new IndexByDocumentSetScope {
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      ret must beEqualTo(Iterable(FileGroupImportJob(fileGroup)))
    }

    "ignore a FileGroup that has no addToDocumentSetId" in new IndexByDocumentSetScope {
      factory.fileGroup(addToDocumentSetId=None)
      ret must beEqualTo(Iterable())
    }

    "ignore a deleted FileGroup" in new IndexByDocumentSetScope {
      val fileGroup = factory.fileGroup(
        deleted=true,
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      ret must beEqualTo(Iterable())
    }
  }

  "indexWithDocumentSetsAndOwners" should {
    trait IndexWithDocumentSetsAndOwnersScope extends BaseScope {
      def ret: Iterable[(ImportJob,DocumentSet,Option[String])] = await(backend.indexWithDocumentSetsAndOwners)
    }

    "return nothing when there are no jobs" in new IndexWithDocumentSetsAndOwnersScope {
      ret must beEqualTo(Iterable())
    }

    "return a DocumentSetCreationJob job" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable(
        (DocumentSetCreationJobImportJob(documentSetCreationJob), documentSet, Some("user@example.org"))
      ))
    }

    "ignore a non-owner" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      factory.documentSetUser(documentSet.id, "owner@example.org")
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      ret must beEqualTo(Iterable(
        (DocumentSetCreationJobImportJob(documentSetCreationJob), documentSet, Some("owner@example.org"))
      ))
    }

    "return a job even that has no owner" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      ret.nonEmpty must beEqualTo(true)
    }

    "ignore a DocumentSetCreationJob of type Recluster" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.InProgress,
        jobType=DocumentSetCreationJobType.Recluster
      )
      ret must beEqualTo(Iterable())
    }

    "ignore a DocumentSetCreationJob that is not InProgress" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentSetCreationJob = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.Cancelled,
        jobType=DocumentSetCreationJobType.DocumentCloud
      )
      ret must beEqualTo(Iterable())
    }

    "return a FileGroup job" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Iterable(
        (FileGroupImportJob(fileGroup), documentSet, Some("user@example.org"))
      ))
    }

    "ignore a FileGroup that has no addToDocumentSetId" in new IndexWithDocumentSetsAndOwnersScope {
      factory.fileGroup(addToDocumentSetId=None)
      ret must beEqualTo(Iterable())
    }

    "ignore a deleted FileGroup" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(
        deleted=true,
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      ret must beEqualTo(Iterable())
    }
  }
}
