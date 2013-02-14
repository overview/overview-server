package models

import helpers.DbTestContext
import models.DatabaseStructure.{ DocumentData, DocumentNodeData, DocumentTagData }
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.Specification

class DocumentTagDataLoaderSpec extends Specification {

  step(start(FakeApplication()))

  "DocumentTagDataLoader" should {
    val loader = new DocumentTagDataLoader

    trait DocumentSetContext extends DbTestContext {
      lazy val documentSetId = insertDocumentSet("DocumentTagDataLoaderSpec")
    }

    trait DocumentsInBranches extends DocumentSetContext {
      var expectedNodeData: Seq[DocumentNodeData] = _
      var documentIds: Seq[Long] = _

      override def setupWithDb = {

        def setupDocumentInBranch(documentId: Long): Seq[DocumentNodeData] = {
          val branch = insertNodes(documentSetId, 3)
          branch.foreach(insertNodeDocument(_, documentId))

          branch.map((documentId, _))
        }

        documentIds = Seq.fill(2)(insertDocument(documentSetId, "description", "dcId"))
        expectedNodeData = documentIds.flatMap(setupDocumentInBranch)
      }
    }

    trait DocumentsLoaded extends DocumentSetContext {
      var expectedDocumentData: Seq[DocumentData] = _
      var documentIds: Seq[Long] = _

      override def setupWithDb = {
        val descriptions = Seq.tabulate(3)(i => "description-" + (5 - i))
        documentIds = descriptions.map(insertDocument(documentSetId, _, "dcId"))

        expectedDocumentData = documentIds.zip(descriptions).map(dt => (dt._1, dt._2, Some("dcId"), None))
      }
    }

    trait DocumentsTagged extends DocumentsLoaded {
      var expectedDocumentTags: Seq[DocumentTagData] = _

      override def setupWithDb = {
        super.setupWithDb

        val tagId1 = insertTag(documentSetId, "tag1")
        val tagId2 = insertTag(documentSetId, "tag2")
        val tagId3 = insertTag(documentSetId, "tag3")

        tagDocuments(tagId1, documentIds.take(2))
        tagDocuments(tagId2, documentIds.take(1))
        val tag1Documents = documentIds.take(2).map((_, tagId1))
        val tag2Documents = documentIds.take(1).map((_, tagId2))
        val tag3Documents = Nil

        expectedDocumentTags = tag1Documents ++ tag2Documents ++ tag3Documents
      }
    }

    "load nodes for documents" in new DocumentsInBranches {
      val nodeData = loader.loadNodes(documentIds)

      nodeData must haveTheSameElementsAs(expectedNodeData)
    }

    "return empty list given no documentIds" in new DbTestContext {
      val nodeData = loader.loadNodes(Nil)

      nodeData must beEmpty
    }

    "return all documents in nodes sorted by description and id" in new DocumentsLoaded {
      val documentData = loader.loadDocuments(documentIds)

      documentData must haveTheSameElementsAs(expectedDocumentData)
      documentData must be equalTo(documentData.sortBy(d => (d._2, d._1)))
    }

    "return no documents if no document ids specified" in new DocumentsLoaded {
      val emptyDocumentIdList = Nil

      val documentData = loader.loadDocuments(emptyDocumentIdList)

      documentData must be empty
    }

    "return tag ids for specified document ids" in new DocumentsTagged {
      val documentTagIds = loader.loadDocumentTags(documentIds)

      documentTagIds must haveTheSameElementsAs(expectedDocumentTags)
    }

    "return no tag ids if no document ids are specified" in new DocumentsTagged {
      val documentTagIds = loader.loadDocumentTags(Nil)

      documentTagIds must be empty
    }

  }

  step(stop)
}
