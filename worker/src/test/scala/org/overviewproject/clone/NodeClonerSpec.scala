package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Node
import org.overviewproject.persistence.DocumentSetIdGenerator

class NodeClonerSpec extends DbSpecification {
  step(setupDb)

  trait CloneContext extends DbTestContext {
    val documentCache: Array[Long] = Seq.tabulate[Long](10)(identity).toArray

    def createTree(documentSetId: Long, parentId: Option[Long], depth: Int): Seq[Node] = {
      if (depth == 0) Nil
      else {
        val child = Node(documentSetId, parentId, "node height: " + depth, 100, documentCache, ids.next)

        Schema.nodes.insert(child)
        child +: createTree(documentSetId, Some(child.id), depth - 1)
      }
    }

    var documentSetCloneId: Long = _
    var sourceNodes: Seq[Node] = _
    var cloneNodes: Seq[Node] = _
    var ids: DocumentSetIdGenerator = _

    override def setupWithDb = {
      val documentSetId = insertDocumentSet("NodeClonerSpec")
      documentSetCloneId = insertDocumentSet("ClonedNodeClonerSpec")

      ids = new DocumentSetIdGenerator(documentSetId)

      sourceNodes = createTree(documentSetId, None, 10)

      NodeCloner.dbClone(documentSetId, documentSetCloneId)
      cloneNodes = Schema.nodes.where(n => n.documentSetId === documentSetCloneId).toSeq
    }
  }

  "NodeCloner" should {

    "create node clones" in new CloneContext {
      cloneNodes.sortBy(_.id).map(_.description) must be equalTo sourceNodes.map(_.description)
    }

    "create clones with ids matching source ids" in new CloneContext {
      val sourceIndeces = sourceNodes.map(n => (n.id << 32) >> 32)
      val cloneIndeces = cloneNodes.map(n => (n.id << 32) >> 32)

      cloneIndeces must haveTheSameElementsAs(sourceIndeces)
    }

    "map document id cache" in new CloneContext {
      val cloneCache = documentCache.map((documentSetCloneId << 32) | _)

      cloneNodes.head.cachedDocumentIds.toSeq must haveTheSameElementsAs(cloneCache)
    }
  }

  step(shutdownDb)
}