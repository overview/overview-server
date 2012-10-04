package models

import anorm._

import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import org.squeryl.Session
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class SubTreeDataLoaderDocumentQuerySpec extends Specification {

  step(start(FakeApplication()))

  "SubTreeDataLoader" should {

    trait DocumentsLoaded extends DbTestContext {
      lazy val documentSetId = insertDocumentSet("SubTreeDataLoaderDocumentQuerySpec")
      lazy val nodeIds = insertNodes(documentSetId, 3)
      val subTreeDataLoader = new SubTreeDataLoader()
    }

    def documentIdsForNodes(nodes: Seq[Long], subTreeDataLoader: SubTreeDataLoader)(implicit connection: Connection): List[Long] = {
      val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(nodes)

      nodeDocumentIds.map(_._3)
    }

    "return 10 document ids at most for a node" in new DocumentsLoaded {
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 15)
      val loadedIds = documentIdsForNodes(nodeIds.take(1), subTreeDataLoader)

      loadedIds must haveTheSameElementsAs(documentIds.take(10))
    }

    "return all document ids if fewer than 10" in new DocumentsLoaded {
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 5)
      val loadedIds = documentIdsForNodes(nodeIds, subTreeDataLoader)

      loadedIds must haveTheSameElementsAs(documentIds)
    }

    "return document ids for several nodes" in new DocumentsLoaded {
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 10)
      val loadedIds = documentIdsForNodes(nodeIds, subTreeDataLoader)

      loadedIds must haveTheSameElementsAs(documentIds)
    }

    "handle nodes with no documents" in new DocumentsLoaded {
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds.take(2), 10)
      val loadedIds = documentIdsForNodes(nodeIds, subTreeDataLoader)

      loadedIds must haveTheSameElementsAs(documentIds.take(20))
    }

    "return empty list for unknown node Id" in new DocumentsLoaded {
      val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(List(1l))

      nodeDocumentIds must be empty
    }

    "returns total document count" in new DocumentsLoaded {
      val numberOfDocuments = 15
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds.take(1), numberOfDocuments)

      val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(nodeIds.take(1))

      val documentCounts = nodeDocumentIds.map(_._2)
      documentCounts.distinct must contain(numberOfDocuments.toLong).only
    }

    "return node ids" in new DocumentsLoaded {
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 10)

      val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(nodeIds)

      val nodes = nodeDocumentIds.map(_._1)
      nodes.distinct must haveTheSameElementsAs(nodeIds)
    }
  }

  step(stop)
}
