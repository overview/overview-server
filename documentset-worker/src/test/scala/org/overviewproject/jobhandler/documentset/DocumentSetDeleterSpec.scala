package org.overviewproject.jobhandler.documentset

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm._
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
import org.overviewproject.database.orm.finders.FinderById
import scala.util.Try

class DocumentSetDeleterSpec extends DbSpecification {

  step(setupDb)

  "DocumentSetDeleter" should {

    trait DocumentSetContext extends DbTestContext {
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
      }

      def addClusteringGeneratedInformation = {
        val tree = Tree(nextTreeId(documentSet.id), documentSet.id, "title", 100, "en", "", "")
        trees.insert(tree)
        val node = Node(nextNodeId(documentSet.id),
          tree.id, None, "", 1, Array(document.id), true)

        nodes.insert(node)
        nodeDocuments.insert(NodeDocument(node.id, document.id))

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
      
      var file: File = _
      
      override protected def createDocumentSet = {
        documentSet = documentSets.insertOrUpdate(DocumentSet(title = "document set"))
        val contentsOid = createContents
        file = files.insertOrUpdate(File(1, contentsOid))
        
        document =
          Document(documentSet.id, fileId = Some(file.id), contentLength = Some(100l))
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
      
      def contentIsRemoved(oid: Long): Boolean = {
        implicit val pgConnection = DB.pgConnection
        
        val findOid = Try( LO.withLargeObject(oid)( lo => lo.oid))
        findOid.isFailure
      }
    }

    "delete client generated information" in new DocumentSetContext {
      addClientGeneratedInformation
      DocumentSetDeleter().deleteClientGeneratedInformation(documentSet.id)

      findAll(logEntries) must beEmpty
      findAllWithFinder(DocumentTagFinder) must beEmpty
      findAll(tags) must beEmpty
      findAll(searchResults) must beEmpty
    }

    inExample("delete clustering generated information") in new DocumentSetContext {
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
      
      findFile must beNone
      contentIsRemoved(file.contentsOid) must beTrue
    }
  }

  step(shutdownDb)

}