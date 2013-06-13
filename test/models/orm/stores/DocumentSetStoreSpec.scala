package models.orm.stores

import java.sql.Timestamp
import java.util.Date
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.SearchResultState._
import org.overviewproject.postgres.LO
import org.overviewproject.tree.orm._
import org.overviewproject.tree.{ DocumentSetCreationJobType, Ownership }
import helpers.{DbTestContext, PgConnectionContext}
import models.Selection
import models.orm._
import models.orm.finders._

class DocumentSetStoreSpec extends Specification {

  trait DocumentSetContext extends PgConnectionContext {
    def insertDocumentSet = {
      DocumentSetStore.insertOrUpdate(DocumentSet(title="title", query=Some("query")))
    }

    def insertUploadedFileAndDocumentSet = {
      val uploadedFile = UploadedFileStore.insertOrUpdate(UploadedFile(contentDisposition = "content-disposition", contentType = "content-type", size = 100))
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(title="title", uploadedFileId = Some(uploadedFile.id)))
      (uploadedFile, documentSet)
    }

    def insertDocument(documentSet : DocumentSet) = {
      DocumentStore.insert(Document(
        id=1L,
        documentType=DocumentType.DocumentCloudDocument,
        documentSetId=documentSet.id,
        documentcloudId=Some("1-hello"),
        description="description"
      ))
    }

    def insertTag(documentSet : DocumentSet) = {
      TagStore.insertOrUpdate(Tag(documentSetId=documentSet.id, name="name"))
    }

    def insertViewer(documentSet: DocumentSet, userEmail: String) = {
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(
        documentSetId=documentSet.id,
        userEmail=userEmail,
        role=Ownership.Viewer
      ))
    }

    def insertNode(documentSet: DocumentSet) = {
      NodeStore.insert(Node(
        id=2L,
        documentSetId=documentSet.id,
        parentId=None,
        description="description",
        cachedSize=0,
        cachedDocumentIds=Array[Long]()
      ))
    }

    def insertDocumentTag(document: Document, tag: Tag) = {
      DocumentTagStore.insertOrUpdate(DocumentTag(documentId=document.id, tagId=tag.id))
    }

    def insertNodeDocument(node: Node, document: Document) = {
      NodeDocumentStore.insertOrUpdate(NodeDocument(nodeId=node.id, documentId=document.id))
    }

    def insertLogEntry(documentSet: DocumentSet) = {
      LogEntryStore.insertOrUpdate(LogEntry(
        documentSetId=documentSet.id,
        userId=1L,
        date=new Timestamp(scala.compat.Platform.currentTime),
        component="component"
      ))
    }

    def insertDocumentProcessingError(documentSet: DocumentSet) = {
      DocumentProcessingErrorStore.insertOrUpdate(DocumentProcessingError(
        documentSetId=documentSet.id,
        textUrl="http://example.org",
        message="message",
        statusCode=Some(500)
      ))
    }
    
    def insertSearchResult(documentSet: DocumentSet) = 
      SearchResultStore.insertOrUpdate(SearchResult(
          state = InProgress,
          documentSetId = documentSet.id,
          query = "query"
      ))
    
    
    def insertDocumentSearchResult(document: Document, searchResult: SearchResult) = 
      DocumentSearchResultStore.insertOrUpdate(DocumentSearchResult(document.id, searchResult.id))
    
  }

  step(start(FakeApplication()))

  "DocumentSetStore" should {
    "set createdAt to the current date by default" in new Scope {
      DocumentSet().createdAt.getTime must beCloseTo((new Date().getTime), 1000)
    }

    "set isPublic to false by default" in new DocumentSetContext {
      DocumentSet().isPublic must beFalse
    }

    "delete document_set_user entries" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet.id, "user@example.org", Ownership.Viewer))

      DocumentSetStore.deleteOrCancelJob(documentSet)

      DocumentSetUserFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete log_entry entries" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      insertLogEntry(documentSet)

      DocumentSetStore.deleteOrCancelJob(documentSet)

      LogEntryFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete tags" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      insertTag(documentSet)

      DocumentSetStore.deleteOrCancelJob(documentSet)

      TagFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete documents" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      insertDocument(documentSet)

      DocumentSetStore.deleteOrCancelJob(documentSet)

      DocumentFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete document_tag entries" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val tag = insertTag(documentSet)
      val document = insertDocument(documentSet)
      insertDocumentTag(document, tag)

      DocumentSetStore.deleteOrCancelJob(documentSet)

      DocumentTagFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete nodes" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      insertNode(documentSet)

      DocumentSetStore.deleteOrCancelJob(documentSet)

      NodeFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete node_document entries" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val document = insertDocument(documentSet)
      val node = insertNode(documentSet)
      insertNodeDocument(node, document)

      DocumentSetStore.deleteOrCancelJob(documentSet)

      NodeDocumentFinder.byDocumentSet(documentSet).count must beEqualTo(0)
      NodeFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete document_set_creation_job entries" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        jobType=DocumentSetCreationJobType.DocumentCloud,
        documentSetId=documentSet.id,
        state=DocumentSetCreationJobState.NotStarted
      ))

      DocumentSetStore.deleteOrCancelJob(documentSet)

      DocumentSetCreationJobFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete uploadedFile for CsvUpload document sets" in new DocumentSetContext {
      val (uploadedFile, documentSet) = insertUploadedFileAndDocumentSet

      DocumentSetStore.deleteOrCancelJob(documentSet.id)

      UploadedFileFinder.byDocumentSet(documentSet).count must beEqualTo(0)
    }

    "delete an uploaded LargeObject when there is a NotStarted job" in new DocumentSetContext {
      val (uploadedFile, documentSet) = insertUploadedFileAndDocumentSet

      val oid = LO.withLargeObject { largeObject =>
        DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
          documentSetId=documentSet.id,
          jobType=DocumentSetCreationJobType.CsvUpload,
          state=DocumentSetCreationJobState.NotStarted,
          contentsOid=Some(largeObject.oid)
        ))
        largeObject.oid
      }

      DocumentSetStore.deleteOrCancelJob(documentSet.id)

      LO.withLargeObject(oid) { lo => } must throwA[java.sql.SQLException]
    }
    
    "delete SearchResults and DocumentSearchResults" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val document = insertDocument(documentSet)
      val searchResult = insertSearchResult(documentSet)
      insertDocumentSearchResult(document, searchResult)
      
      DocumentSetStore.deleteOrCancelJob(documentSet.id)
      val searchResultSelection = Selection(documentSet.id, Nil, Nil, Seq(searchResult.id), Nil)
      DocumentFinder.bySelection(searchResultSelection).count must be equalTo(0)
      SearchResultFinder.byDocumentSet(documentSet.id).count must be equalTo(0)
    }

    "cancel in-progress clone jobs when deleting a document set" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val cloneDocumentSet = CloneImportJobStore.insertCloneOf(documentSet)
      val cloneJob = DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        documentSetId=cloneDocumentSet.id,
        jobType=DocumentSetCreationJobType.Clone,
        state=DocumentSetCreationJobState.InProgress,
        sourceDocumentSetId=Some(documentSet.id)
      ))

      DocumentSetStore.deleteOrCancelJob(documentSet.id)

      val cancelledJob = DocumentSetCreationJobFinder.byDocumentSet(cloneDocumentSet).headOption
      cancelledJob must beSome like { case Some(job) => job.state must beEqualTo(DocumentSetCreationJobState.Cancelled) }
    }

    "cancel a job and delete client-generated information if a job is in progress" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      val tag = insertTag(documentSet)
      val document = insertDocument(documentSet)
      val node = insertNode(documentSet)
      insertLogEntry(documentSet)
      insertViewer(documentSet, "user@example.org")
      insertDocumentTag(document, tag)
      insertNodeDocument(node, document)
      insertDocumentProcessingError(documentSet)
      val job = DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        documentSetId=documentSet.id,
        jobType=DocumentSetCreationJobType.DocumentCloud,
        state=DocumentSetCreationJobState.InProgress
      ))

      DocumentSetStore.deleteOrCancelJob(documentSet)

      // These should be deleted
      LogEntryFinder.byDocumentSet(documentSet).count must beEqualTo(0)
      DocumentSetUserFinder.byDocumentSet(documentSet).count must beEqualTo(0)
      TagFinder.byDocumentSet(documentSet).count must beEqualTo(0)
      DocumentTagFinder.byDocumentSet(documentSet).count must beEqualTo(0)
      // These shouldn't
      DocumentFinder.byDocumentSet(documentSet).count must beEqualTo(1)
      NodeFinder.byDocumentSet(documentSet).count must beEqualTo(1)
      NodeDocumentFinder.byDocumentSet(documentSet).count must beEqualTo(1)
      DocumentProcessingErrorFinder.byDocumentSet(documentSet).count must beEqualTo(1)
      // The job must exist, cancelled
      val cancelledJob = DocumentSetCreationJobFinder.byDocumentSet(documentSet).headOption
      cancelledJob must beSome like { case Some(job) => job.state must beEqualTo(DocumentSetCreationJobState.Cancelled) }
    }
  }

  step(stop)
}
