package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Node
import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.Tree

class NodeClonerSpec extends DbSpecification {
  step(setupDb)

  trait CloneContext extends DbTestContext {
    val documentCache: Array[Long] = Seq.tabulate[Long](10)(identity).toArray

    def createTreeNodes(treeId: Long, parentId: Option[Long], depth: Int): Seq[Node] = {
      import org.overviewproject.postgres.SquerylEntrypoint._
      if (depth == 0) Nil
      else {
        val child = Node(
          id=ids.next,
          treeId = treeId,
          parentId=parentId,
          description="node height: " + depth,
          cachedSize=100,
          isLeaf=(depth == 1)
        )

        Schema.nodes.insert(child)
        child +: createTreeNodes(treeId, Some(child.id), depth - 1)
      }
    }

    var documentSetClone: DocumentSet = _
    var sourceNodes: Seq[Node] = _
    var cloneNodes: Seq[Node] = _
    var ids: DocumentSetIdGenerator = _

    override def setupWithDb = {
      import org.overviewproject.postgres.SquerylEntrypoint._
      val documentSet = Schema.documentSets.insertOrUpdate(DocumentSet(title = "NodeClonerSpec"))
      documentSetClone = Schema.documentSets.insertOrUpdate(DocumentSet(title = "clone"))
      
      val treeIds = new DocumentSetIdGenerator(documentSet.id)
      val tree = Tree(treeIds.next, documentSet.id, 0L, "tree", 100, "en", "", "")
  
      val cloneTreeIds = new DocumentSetIdGenerator(documentSetClone.id)
      val cloneTree = tree.copy(id = cloneTreeIds.next)
      
      Schema.trees.insert(tree)
      Schema.trees.insert(cloneTree)
      
      ids = new DocumentSetIdGenerator(documentSet.id)

      sourceNodes = createTreeNodes(tree.id, None, 10)

      NodeCloner.clone(documentSet.id, documentSetClone.id)
      cloneNodes = Schema.nodes.where(n => n.treeId === cloneTree.id).toSeq
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
      val sourceIndices = sourceNodes.map(n => (n.id << 32) >> 32)
      val cloneIndices = cloneNodes.map(n => (n.id << 32) >> 32)

      cloneIndices must containTheSameElementsAs(sourceIndices)
    }
  }

  step(shutdownDb)
}
