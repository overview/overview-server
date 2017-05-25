package controllers.backend

import java.time.Instant

import com.overviewdocs.models.{CloneImportJob,CsvImportJob,DocumentSet,DocumentCloudImportJob,DocumentSetUser,FileGroupImportJob,ImportJob}

class DbImportJobBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbImportJobBackend {}
  }

  "indexByUser" should {
    trait IndexByUserScope extends BaseScope {
      def ret: Seq[ImportJob] = await(backend.indexByUser("user@example.org"))
    }

    "return nothing when there are no jobs" in new IndexByUserScope {
      ret must beEqualTo(Seq())
    }

    "return a DocumentCloudImport job" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq(DocumentCloudImportJob(documentCloudImport)))
    }

    "return a DocumentSet that the user only views (does not own)" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      ret must beEqualTo(Seq(DocumentCloudImportJob(documentCloudImport)))
    }

    "ignore a DocumentSet that belongs to a different user" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "user2@example.org")
      ret must beEqualTo(Seq())
    }

    "return a CloneImportJob" in new IndexByUserScope {
      val cloneJob = factory.cloneJob(
        sourceDocumentSetId=factory.documentSet().id,
        destinationDocumentSetId=factory.documentSet().id
      )
      factory.documentSetUser(cloneJob.destinationDocumentSetId, "user@example.org")
      ret must beEqualTo(Seq(CloneImportJob(cloneJob)))
    }

    "return a CsvImportJob" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val csvImport = factory.csvImport(
        documentSetId=documentSet.id,
        nBytes=1L
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq(CsvImportJob(csvImport)))
    }

    "return a FileGroup job" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        ocr=Some(true),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq(FileGroupImportJob(fileGroup)))
    }

    "ignore a FileGroup that has no addToDocumentSetId" in new IndexByUserScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(addToDocumentSetId=None)
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq())
    }
  }

  "indexByDocumentSet" should {
    trait IndexByDocumentSetScope extends BaseScope {
      val documentSet = factory.documentSet()
      def ret: Seq[ImportJob] = await(backend.indexByDocumentSet(documentSet.id))
    }

    "return nothing when there are no jobs" in new IndexByDocumentSetScope {
      ret must beEqualTo(Seq())
    }

    "return a DocumentCloudImport job" in new IndexByDocumentSetScope {
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      ret must beEqualTo(Seq(DocumentCloudImportJob(documentCloudImport)))
    }

    "ignore a DocumentCloudImport of a different DocumentSet" in new IndexByDocumentSetScope {
      factory.documentCloudImport(documentSetId=factory.documentSet().id)
      ret must beEqualTo(Seq())
    }

    "return a CloneImportJob" in new IndexByDocumentSetScope {
      val cloneJob = factory.cloneJob(
        sourceDocumentSetId=factory.documentSet().id,
        destinationDocumentSetId=documentSet.id
      )
      ret must beEqualTo(Seq(CloneImportJob(cloneJob)))
    }

    "return a CsvImportJob" in new IndexByDocumentSetScope {
      val csvImport = factory.csvImport(documentSetId=documentSet.id, nBytes=1L)
      ret must beEqualTo(Seq(CsvImportJob(csvImport)))
    }

    "return a FileGroup job" in new IndexByDocumentSetScope {
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        ocr=Some(true),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      ret must beEqualTo(Seq(FileGroupImportJob(fileGroup)))
    }

    "ignore a FileGroup that has no addToDocumentSetId" in new IndexByDocumentSetScope {
      factory.fileGroup(addToDocumentSetId=None)
      ret must beEqualTo(Seq())
    }
  }

  "indexWithDocumentSetsAndOwners" should {
    trait IndexWithDocumentSetsAndOwnersScope extends BaseScope {
      def ret: Seq[(ImportJob,DocumentSet,Option[String])] = await(backend.indexWithDocumentSetsAndOwners)
    }

    "return nothing when there are no jobs" in new IndexWithDocumentSetsAndOwnersScope {
      ret must beEqualTo(Seq())
    }

    "return a DocumentCloudImport job" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq(
        (DocumentCloudImportJob(documentCloudImport), documentSet, Some("user@example.org"))
      ))
    }

    "ignore a non-owner" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      factory.documentSetUser(documentSet.id, "owner@example.org")
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      ret must beEqualTo(Seq(
        (DocumentCloudImportJob(documentCloudImport), documentSet, Some("owner@example.org"))
      ))
    }

    "return a job even that has no owner" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val documentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
      ret.nonEmpty must beEqualTo(true)
    }

    "return a CloneImportJob" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val cloneJob = factory.cloneJob(
        sourceDocumentSetId=factory.documentSet().id,
        destinationDocumentSetId=documentSet.id
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq((CloneImportJob(cloneJob), documentSet, Some("user@example.org"))))
    }

    "return a CsvImportJob" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user@example.org")
      val csvImport = factory.csvImport(documentSetId=documentSet.id, nBytes=1L)
      ret must beEqualTo(Seq((CsvImportJob(csvImport), documentSet, Some("user@example.org"))))
    }

    "return a FileGroup job" in new IndexWithDocumentSetsAndOwnersScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(false),
        ocr=Some(true),
        nFiles=Some(4),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(20L),
        estimatedCompletionTime=Some(Instant.now)
      )
      factory.documentSetUser(documentSet.id, "user@example.org")
      ret must beEqualTo(Seq(
        (FileGroupImportJob(fileGroup), documentSet, Some("user@example.org"))
      ))
    }

    "ignore a FileGroup that has no addToDocumentSetId" in new IndexWithDocumentSetsAndOwnersScope {
      factory.fileGroup(addToDocumentSetId=None)
      ret must beEqualTo(Seq())
    }
  }
}
