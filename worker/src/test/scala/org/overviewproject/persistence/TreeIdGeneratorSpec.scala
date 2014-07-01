package org.overviewproject.persistence

import org.overviewproject.test.{ DbSpecification, IdGenerator }
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.tree.orm.{ DocumentSet, Node, Tree }


class TreeIdGeneratorSpec extends DbSpecification {
  step(setupDb)

  "TreeIdGenerator" should {

    trait DocumentSetSetup extends DbTestContext {
      var documentSet: DocumentSet = _

      override def setupWithDb = {
        documentSet = Schema.documentSets.insert(DocumentSet(title = "TreeIdGeneratorSpec"))
      }
    }

    trait TreeSetup extends DocumentSetSetup {
      val treeIndex = 5

      def createTree(documentSetId: Long, index: Long) : Unit = {
        val rootId = IdGenerator.nextNodeId(documentSetId)
        val treeId = (documentSetId << 32) | index
        Schema.nodes.insert(Node(rootId, rootId, None, "description", 100, true))
        Schema.trees.insert(Tree(
          id=treeId,
          documentSetId=documentSetId,
          rootNodeId=rootId,
          jobId=0L,
          title="a tree",
          documentCount=100,
          lang="en"
        ))
      }

      override def setupWithDb = {
        super.setupWithDb

        createTree(documentSet.id, 1)
        createTree(documentSet.id, treeIndex)
      }
    }

    "generate first id if there are no trees" in new DocumentSetSetup {
      val id = TreeIdGenerator.next(documentSet.id)

      id must be equalTo ((documentSet.id << 32) | 1)
    }

    "generate the id following the largest found tree id" in new TreeSetup {
      val id = TreeIdGenerator.next(documentSet.id)

      id must be equalTo ((documentSet.id <<32) | (treeIndex + 1))
    }
  }

  step(shutdownDb)
}
