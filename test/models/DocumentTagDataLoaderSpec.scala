package models

import helpers.DbSetup._
import helpers.DbTestContext
import models.DatabaseStructure.{DocumentData, DocumentNodeData}
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class DocumentTagDataLoaderSpec extends Specification {

  step(start(FakeApplication()))

  "DocumentTagDataLoader" should {
    val loader = new DocumentTagDataLoader

    trait DocumentsInBranches extends DbTestContext {
      var expectedNodeData: Seq[DocumentNodeData] = _
      var documentIds: Seq[Long] = _

      override def setupWithDb = {
        val documentSetId = insertDocumentSet("DocumentTagLoaderSpec")

        def setupDocumentInBranch(documentId: Long): Seq[DocumentNodeData] = {
          val branch = insertNodes(documentSetId, 3)
          branch.foreach(insertNodeDocument(_, documentId))

          branch.map((documentId, _))
        }

        documentIds = Seq.fill(2)(insertDocument(documentSetId, "title", "dcId"))
        expectedNodeData = documentIds.flatMap(setupDocumentInBranch)
      }
    }


    trait DocumentsLoaded extends DbTestContext {
      var expectedDocumentData: Seq[DocumentData] = _
      var documentIds: Seq[Long] = _
      
      override def setupWithDb = {
	val documentSetId = insertDocumentSet("SubTreeDataLoaderDocumentQuerySpec")
	documentIds = Seq.fill(3)(insertDocument(documentSetId, "title", "dcId"))

	expectedDocumentData = documentIds.map((_, "title", "dcId"))
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


    "return all documents in nodes" in new DocumentsLoaded {
      val documentData = loader.loadDocuments(documentIds)

      documentData must haveTheSameElementsAs(expectedDocumentData)
    }


    "return no documents if no document ids specified" in new DocumentsLoaded {
      val emptyDocumentIdList = Nil

      val documentData = loader.loadDocuments(emptyDocumentIdList)

      documentData must be empty
    }    
  }

  step(stop)
}
