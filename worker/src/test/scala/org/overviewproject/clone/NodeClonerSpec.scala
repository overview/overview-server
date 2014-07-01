package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Node
import org.overviewproject.persistence.{ DocumentSetIdGenerator, NodeIdGenerator }

class NodeClonerSpec extends DbSpecification {
  step(setupDb)

  trait CloneContext extends DbTestContext {
    def createTreeNodes(ids: NodeIdGenerator, parentId: Option[Long], depth: Int): Seq[Node] = {
      import org.overviewproject.postgres.SquerylEntrypoint._
      if (depth == 0) Nil
      else {
        val child = Node(
          id=ids.next,
          rootId = ids.rootId,
          parentId=parentId,
          description="node height: " + depth,
          cachedSize=100,
          isLeaf=(depth == 1)
        )

        Schema.nodes.insert(child)
        child +: createTreeNodes(ids, Some(child.id), depth - 1)
      }
    }

    var sourceNodes: Seq[Node] = _
    var cloneNodes: Seq[Node] = _

    override def setupWithDb = {
      import org.overviewproject.postgres.SquerylEntrypoint._

      val documentSetId = 1L
      val cloneDocumentSetId = 2L

      val treeId = new DocumentSetIdGenerator(documentSetId).next
      val cloneTreeId = new DocumentSetIdGenerator(cloneDocumentSetId).next

      val ids = new NodeIdGenerator(treeId)
      sourceNodes = createTreeNodes(ids, None, 10)

      val cloneRootNodeId = new NodeIdGenerator(cloneTreeId).rootId
      NodeCloner.clone(documentSetId, cloneDocumentSetId)

      cloneNodes = Schema.nodes.where(n => n.rootId === cloneRootNodeId).toIndexedSeq
    }
  }

  "NodeCloner" should {
    "clone descriptions" in new CloneContext {
      cloneNodes.sortBy(_.id).map(_.description) must be equalTo sourceNodes.map(_.description)
    }

    "clone cachedSizes" in new CloneContext {
      cloneNodes.sortBy(_.id).map(_.cachedSize) must be equalTo sourceNodes.map(_.cachedSize)
    }

    "create clones with ids matching source ids" in new CloneContext {
      val sourceIndices = sourceNodes.map(n => (n.id << 32) >> 32).toSeq.sorted
      val cloneIndices = cloneNodes.map(n => (n.id << 32) >> 32).toSeq.sorted

      cloneIndices must beEqualTo(sourceIndices)
    }
  }

  step(shutdownDb)
}
