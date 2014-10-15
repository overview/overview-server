package org.overviewproject.jobhandler.documentset

import org.overviewproject.models.ApiToken
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType._
import java.sql.Timestamp
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.test.IdGenerator._
import org.overviewproject.database.orm.Schema._
import org.overviewproject.database.orm.finders.DocumentTagFinder
import org.squeryl.Table
import org.overviewproject.database.orm.finders.FindableByDocumentSet
import org.overviewproject.database.orm.finders.NodeDocumentFinder
import org.overviewproject.postgres.LO
import org.overviewproject.database.DB
import org.overviewproject.tree.orm.finders.FinderById
import scala.util.Try

class DocumentSetDeleterSpec extends DbSpecification {

  step(setupDb)

  "DocumentSetDeleter" should {

    trait DocumentSetContext extends DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._
      var documentSet: DocumentSet = _

      protected var document: Document = _

      override def setupWithDb = {
        createDocumentSet
      }

      protected def createDocumentSet: Unit = {
        documentSet = documentSets.insertOrUpdate(DocumentSet(title = "document set"))
        document = Document(documentSet.id, documentcloudId = Some("dcId"), id = nextDocumentId(documentSet.id))
        documents.insert(document)
      }

      def addJobInformation = {
        val jobs = Seq.fill(2)(DocumentSetCreationJob(
          documentSetId = documentSet.id,
          jobType = Recluster,
          treeTitle = Some("failed reclustering"),
          state = Error))
        documentSetCreationJobs.insert(jobs)
      }

      def addCancelledJobInformation = {
        val job = documentSetCreationJobs.insert(
          DocumentSetCreationJob(
            documentSetId = documentSet.id,
            jobType = Recluster,
            treeTitle = Some("Cancelled recluster job"),
            state = Cancelled))
      }

      def addClientGeneratedInformation = {
        logEntries.insert(
          LogEntry(documentSetId = documentSet.id,
            userId = 1l,
            date = new Timestamp(0l),
            component = ""))
        val tag = tags.insertOrUpdate(Tag(documentSetId = documentSet.id, name = "", color = "ffffff"))
        documentTags.insert(DocumentTag(document.id, tag.id))
        val searchResult = searchResults.insertOrUpdate(SearchResult(SearchResultState.Complete, documentSet.id, "query"))
        documentSearchResults.insert(DocumentSearchResult(document.id, searchResult.id))
        val apiToken = ApiToken(
          token="12345",
          documentSetId=documentSet.id,
          description="description",
          createdAt=new Timestamp(0L),
          createdBy="user@example.org"
        )
        apiTokens.insert(apiToken)
      }

      def addClusteringGeneratedInformation = {
        val nodeId = nextNodeId(documentSet.id)
        val node = nodes.insert(Node(nodeId, nodeId, None, "", 1, true))
        nodeDocuments.insert(NodeDocument(node.id, document.id))
        trees.insert(Tree(nextTreeId(documentSet.id), documentSet.id, node.id, 0L, "title", 100, "en"))

        documentProcessingErrors.insert(
          DocumentProcessingError(documentSet.id, "url", "message", None, None))
      }

      def findAll[A <: DocumentSetComponent](table: Table[A]): Seq[A] =
        DocumentSetComponentFinder(table).byDocumentSet(documentSet.id).toSeq

      def findAllWithFinder[A](finder: FindableByDocumentSet[A]): Seq[A] =
        finder.byDocumentSet(documentSet.id).toSeq

      def findAllNodes: Seq[Node] =
        from(nodes)(select(_)).toSeq

      def findDocumentSet: Option[DocumentSet] =
        from(documentSets)(ds =>
          where(ds.id === documentSet.id)
            select (ds)).headOption
    }

    trait CsvUploadContext extends DocumentSetContext {
      import org.overviewproject.postgres.SquerylEntrypoint._
      var uploadedFile: UploadedFile = _

      override protected def createDocumentSet = {
        uploadedFile = uploadedFiles.insertOrUpdate(
          UploadedFile(contentDisposition = "disp", contentType = "type", size = 100l))

        documentSet = documentSets.insertOrUpdate(
          DocumentSet(title = "document set", uploadedFileId = Some(uploadedFile.id)))
        document = Document(documentSet.id, documentcloudId = Some("dcId"), id = nextDocumentId(documentSet.id))
        documents.insert(document)
      }

      def findUploadedFile: Option[UploadedFile] = {
        val finder = new FinderById(uploadedFiles)
        finder.byId(uploadedFile.id).headOption
      }
    }

    trait PdfUploadContext extends DocumentSetContext {
      import org.overviewproject.postgres.SquerylEntrypoint._
      var file: File = _
      var page: Page = _

      override protected def createDocumentSet = {
        documentSet = documentSets.insertOrUpdate(DocumentSet(title = "document set"))
        val contentsOid = createContents
        file = files.insertOrUpdate(File(1, contentsOid, contentsOid, "name", Some(100), Some(100)))
        val pageSize = 128
        val pageData: Array[Byte] = Array.fill(pageSize)(0xfe.toByte)
        page = pages.insertOrUpdate(Page(file.id, 1, 1, Some(pageData), Some(pageSize), Some("Text")))

        
        document = Document(documentSet.id, fileId = Some(file.id), pageId = Some(page.id))
        documents.insert(document)
      }

      private def createContents: Long = {
        implicit val pgConnection = DB.pgConnection

        LO.withLargeObject(_.oid)
      }

      def findFile: Option[File] = {
        val finder = new FinderById(files)
        finder.byId(file.id).headOption
      }

      def findPages: Iterable[Page] = {
        from(pages)(p =>
          where(p.fileId === file.id)
            select (p))
      }

      // Generates an exception which aborts the transaction, so no further database access is possible
      // Use this assertion at the end of the test only
      def contentIsRemoved(oid: Long): Boolean = {
        implicit val pgConnection = DB.pgConnection

        val findOid = Try(LO.withLargeObject(oid)(lo => lo.oid))
        findOid.isFailure
      }
    }

    trait FailedPdfUploadContext extends PdfUploadContext {
      import org.overviewproject.postgres.SquerylEntrypoint._
      override protected def createDocumentSet = {
        super.createDocumentSet
        tempDocumentSetFiles.insertOrUpdate(TempDocumentSetFile(documentSet.id, file.id))
      }
      
      def findTempDocumentSetFiles: Iterable[TempDocumentSetFile] = 
        from(tempDocumentSetFiles)(dsf =>
          where (dsf.documentSetId === documentSet.id)  
          select (dsf)
        )
    }
    
    trait ReclusterContext extends DocumentSetContext {
      override def createDocumentSet = {
        super.createDocumentSet
        addCancelledJobInformation
      }
    }

    "delete job information" in new DocumentSetContext {
      addJobInformation
      DocumentSetDeleter().deleteJobInformation(documentSet.id)

      findAll(documentSetCreationJobs) must beEmpty
    }

    "delete client generated information" in new DocumentSetContext {
      addClientGeneratedInformation
      DocumentSetDeleter().deleteClientGeneratedInformation(documentSet.id)

      findAll(apiTokens) must beEmpty
      findAll(logEntries) must beEmpty
      findAllWithFinder(DocumentTagFinder) must beEmpty
      findAll(tags) must beEmpty
      findAll(searchResults) must beEmpty
    }

    "delete clustering generated information" in new DocumentSetContext {
      addClusteringGeneratedInformation
      DocumentSetDeleter().deleteClusteringGeneratedInformation(documentSet.id)

      findAll(trees) must beEmpty
      findAllNodes must beEmpty
      findAllWithFinder(NodeDocumentFinder) must beEmpty
      findAll(documentProcessingErrors) must beEmpty
    }

    "delete document set" in new DocumentSetContext {
      DocumentSetDeleter().deleteDocumentSet(documentSet.id)

      findAll(documents) must beEmpty
      findAll(documentSetUsers) must beEmpty
      findDocumentSet must beNone
    }

    "delete document set with CSV upload" in new CsvUploadContext {
      DocumentSetDeleter().deleteDocumentSet(documentSet.id)

      findUploadedFile must beNone
    }

    "delete document set with uploaded PDFs" in new PdfUploadContext {
      DocumentSetDeleter().deleteDocumentSet(documentSet.id)

      findPages must beEmpty
      findFile must beNone
      contentIsRemoved(file.contentsOid) must beTrue
    }
    
    "delete failed upload job" in new FailedPdfUploadContext {
      DocumentSetDeleter().deleteDocumentSet(documentSet.id)
      
      findTempDocumentSetFiles must beEmpty
    }
    "delete cancelled reclustering job" in new ReclusterContext {
      DocumentSetDeleter().deleteCancelledJobInformation(documentSet.id)
      
      findAll(documentSetCreationJobs) must beEmpty
      findDocumentSet must beSome
    }
  }

  step(shutdownDb)

}
