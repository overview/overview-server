package com.overviewdocs.clone

import org.specs2.mock.Mockito
import play.api.libs.json.Json
import scala.concurrent.Future

import com.overviewdocs.models._
import com.overviewdocs.models.tables._
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.test.factories.{DbFactory=>factory}

class ClonerSpec extends DbSpecification with Mockito {
  sequential

  "Cloner" should {
    trait BaseScope extends DbScope {
      val sourceDocumentSet = factory.documentSet(id=123L)
      val destinationDocumentSet = factory.documentSet(id=234L)
      val stepNumber: Short = 0
      val cancelled: Boolean = false
      val mockIndexer = smartMock[Indexer]
      mockIndexer.indexDocuments(any) returns Future.successful(())
      lazy val cloneJob = factory.cloneJob(
        sourceDocumentSetId=sourceDocumentSet.id,
        destinationDocumentSetId=destinationDocumentSet.id,
        stepNumber=stepNumber,
        cancelled=cancelled
      )

      def go = {
        val cloner = new Cloner {
          override protected val indexer = mockIndexer
        }
        await(cloner.run(cloneJob))
      }

      def dbDocuments = {
        import database.api._
        blockingDatabase.seq(Documents.filter(_.documentSetId === destinationDocumentSet.id).sortBy(_.id))
      }

      def dbFiles = {
        import database.api._
        blockingDatabase.seq(Files.sortBy(_.id))
      }

      def dbTags = {
        import database.api._
        // We sort by name, not ID. Cloning might put tags in a different order,
        // and we don't care. The caller must create tags in alphabetical order
        // to make unit tests work.
        blockingDatabase.seq(Tags.filter(_.documentSetId === destinationDocumentSet.id).sortBy(_.name))
      }

      def dbDocumentTags = {
        import database.api._
        blockingDatabase.seq(
          DocumentTags
            .filter(_.documentId >= (234L << 32))
            .sortBy(dt => (dt.documentId, dt.tagId))
        )
      }

      def dbTrees = {
        import database.api._
        blockingDatabase.seq(Trees.filter(_.documentSetId === destinationDocumentSet.id).sortBy(_.id))
      }

      def dbNodes = {
        import database.api._
        blockingDatabase.seq(
          Nodes
            .filter(_.id >= (destinationDocumentSet.id << 32))
            .filter(_.id < ((destinationDocumentSet.id + 1) << 32))
            .sortBy(_.id)
        )
      }

      def dbNodeDocuments = {
        import database.api._
        blockingDatabase.seq(
          NodeDocuments
            .filter(_.documentId >= (destinationDocumentSet.id << 32))
            .filter(_.documentId < ((destinationDocumentSet.id + 1) << 32))
            .sortBy(nd => (nd.nodeId, nd.documentId))
        )
      }
    }

    "clone document text" in new BaseScope {
      factory.document(documentSetId=sourceDocumentSet.id, id=1L, text="foo")
      factory.document(documentSetId=sourceDocumentSet.id, id=2L, text="bar")
      go
      dbDocuments.map(_.text) must beEqualTo(Seq("foo", "bar"))
    }

    "clone document metadata" in new BaseScope {
      // https://www.pivotaltracker.com/story/show/99507728
      factory.document(documentSetId=sourceDocumentSet.id, id=1L, metadataJson=Json.obj("foo" -> "bar"))
      factory.document(documentSetId=sourceDocumentSet.id, id=2L, metadataJson=Json.obj("bar" -> "baz"))
      go
      dbDocuments.map(_.metadataJson) must beEqualTo(
        Seq(Json.obj("foo" -> "bar"), Json.obj("bar" -> "baz"))
      )
    }

    "give IDs corresponding to the new DocumentSet" in new BaseScope {
      factory.document(documentSetId=sourceDocumentSet.id, id=528280977408L)
      factory.document(documentSetId=sourceDocumentSet.id, id=528280977409L)
      go
      dbDocuments.map(_.id) must beEqualTo(Seq(1005022347264L, 1005022347265L))
    }

    "index the cloned documents" in new BaseScope {
      go
      there was one(mockIndexer).indexDocuments(234L)
    }

    "refer to the same Files" in new BaseScope {
      val file = factory.file(referenceCount=1)
      factory.document(documentSetId=sourceDocumentSet.id, fileId=Some(file.id))
      go
      dbDocuments.map(_.fileId) must beEqualTo(Seq(Some(file.id)))
      dbFiles.map(_.id) must beEqualTo(Seq(file.id))
    }

    "refer to the same Pages" in new BaseScope {
      val file = factory.file(referenceCount=1)
      val page = factory.page(fileId=file.id)
      factory.document(documentSetId=sourceDocumentSet.id, fileId=Some(file.id), pageId=Some(page.id))
      go
      dbDocuments.map(_.pageId) must beEqualTo(Seq(Some(page.id)))
    }

    "copy the pageNumbers" in new BaseScope {
      factory.document(documentSetId=sourceDocumentSet.id, pageNumber=Some(123))
      go
      dbDocuments.map(_.pageNumber) must beEqualTo(Seq(Some(123)))
    }

    "increment File referenceCount" in new BaseScope {
      val file = factory.file(referenceCount=1)
      factory.document(documentSetId=sourceDocumentSet.id, fileId=Some(file.id))
      factory.document(documentSetId=sourceDocumentSet.id, fileId=Some(file.id))
      go
      dbFiles.map(_.referenceCount) must beEqualTo(Seq(2))
    }

    "clone DocumentProcessingErrors" in new BaseScope {
      factory.documentProcessingError(documentSetId=sourceDocumentSet.id)
      factory.documentProcessingError(documentSetId=sourceDocumentSet.id)
      go
      blockingDatabase.length(DocumentProcessingErrors) must beEqualTo(4)
    }

    "clone Tags" in new BaseScope {
      factory.tag(documentSetId=sourceDocumentSet.id, name="foo")
      go
      dbTags.map(_.name) must beEqualTo(Seq("foo"))
    }

    "clone DocumentTags" in new BaseScope {
      val tag1 = factory.tag(documentSetId=sourceDocumentSet.id, name="tag1", id=1L)
      val tag2 = factory.tag(documentSetId=sourceDocumentSet.id, name="tag2", id=2L)
      val doc1 = factory.document(documentSetId=sourceDocumentSet.id, id=1L)
      val doc2 = factory.document(documentSetId=sourceDocumentSet.id, id=2L)
      val doc3 = factory.document(documentSetId=sourceDocumentSet.id, id=3L)
      factory.documentTag(doc1.id, tag1.id)
      factory.documentTag(doc2.id, tag1.id)
      factory.documentTag(doc2.id, tag2.id)
      go
      val newIds = dbTags.map(_.id)
      dbDocumentTags must containTheSameElementsAs(Seq(
        DocumentTag((234L << 32) | 1, newIds(0)),
        DocumentTag((234L << 32) | 2, newIds(0)),
        DocumentTag((234L << 32) | 2, newIds(1))
      ))
    }

    "clone Trees" in new BaseScope {
      val tree = factory.tree(documentSetId=123L, id=(123L << 32) | 1)
      go
      val res = dbTrees
      dbTrees.length must beEqualTo(1)
      dbTrees.head must beEqualTo(tree.copy(
        documentSetId=234L,
        id=(234L << 32) | 1,
        createdAt=dbTrees.head.createdAt
      ))
    }

    "clone Nodes" in new BaseScope {
      val node1 = factory.node(id=(123L << 32), rootId=(123L << 32))
      val node2 = factory.node(id=(123L << 32) | 1, rootId=(123L << 32), parentId=Some(123L << 32))
      go
      dbNodes must beEqualTo(Seq(
        node1.copy(id=(234L << 32), rootId=(234L << 32)),
        node2.copy(id=(234L << 32) | 1, rootId=(234L << 32), parentId=Some(234L << 32))
      ))
    }

    "clone NodeDocuments" in new BaseScope {
      val node1 = factory.node(id=(123L << 32), rootId=(123L << 32))
      val node2 = factory.node(id=(123L << 32) | 1, rootId=(123L << 32), parentId=Some(123L << 32))
      val doc1 = factory.document(documentSetId=sourceDocumentSet.id, id=(123L << 32) | 2)
      val doc2 = factory.document(documentSetId=sourceDocumentSet.id, id=(123L << 32) | 3)
      factory.nodeDocument(node1.id, doc1.id)
      factory.nodeDocument(node1.id, doc2.id)
      factory.nodeDocument(node2.id, doc1.id)
      go
      dbNodeDocuments must beEqualTo(Seq(
        NodeDocument(234L << 32, (234L << 32) | 2),
        NodeDocument(234L << 32, (234L << 32) | 3),
        NodeDocument((234L << 32) | 1, (234L << 32) | 2)
      ))
    }

    "resume after copying documents" in new BaseScope {
      val doc1 = factory.document(documentSetId=123L, id=(123L << 32) | 1)
      factory.document(documentSetId=234L, id=(234L << 32) | 1)
      override val stepNumber = 1.toShort
      go
      dbDocuments.length must beEqualTo(1)
    }

    "delete the job when complete" in new BaseScope {
      go

      import database.api._
      blockingDatabase.seq(CloneJobs).length must beEqualTo(0)
    }

    "delete the job when cancelled" in new BaseScope {
      factory.document(documentSetId=123L, id=(123L << 32) | 1)
      override val cancelled = true
      go
      dbDocuments.length must beEqualTo(0)
    }
  }
}
