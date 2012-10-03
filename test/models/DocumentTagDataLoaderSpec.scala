package models

import helpers.DbSetup._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class DocumentTagDataLoaderSpec extends Specification {

  step(start(FakeApplication()))

  "DocumentTagLoader" should {
    "load nodes for documents" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentTagLoaderSpec")
      val branch1 = insertNodes(documentSetId, 3)
      val document1 = insertDocument(documentSetId, "title1", "dcId1")
      branch1.foreach(insertNodeDocument(_, document1))
      val document1NodeData = branch1.map((document1, _))
      
      val branch2 = insertNodes(documentSetId, 3)
      val document2 = insertDocument(documentSetId, "title2", "dcId2")
      branch2.foreach(insertNodeDocument(_, document2))
      val document2NodeData = branch2.map((document2, _))

      val expectedNodeData = document1NodeData ++ document2NodeData
      
      val loader = new DocumentTagDataLoader
      val nodeData = loader.loadNodes(Seq(document1, document2))

      nodeData must haveTheSameElementsAs(expectedNodeData)
    }
  }

  step(stop)
}
