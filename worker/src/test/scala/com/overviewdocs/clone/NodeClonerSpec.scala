package com.overviewdocs.clone

import com.overviewdocs.models.Node
import com.overviewdocs.models.tables.Nodes
import com.overviewdocs.test.DbSpecification

class NodeClonerSpec extends DbSpecification {
  "NodeCloner" should {
    trait BaseScope extends DbScope {
      import database.api._

      val originalDocumentSet = factory.documentSet(id=1L)
      val cloneDocumentSet = factory.documentSet(id=2L)

      def go = NodeCloner.clone(originalDocumentSet.id, cloneDocumentSet.id)

      def findNodes(rootId: Long): Seq[Node] = blockingDatabase.seq {
        Nodes
          .filter(_.rootId === rootId)
          .sortBy(_.id)
      }
    }

    "clone nodes" in new BaseScope {
      val rootId = 1L << 32
      factory.node(rootId + 0, rootId, None, "root", 2, false)
      factory.node(rootId + 1, rootId, Some(rootId), "1-1", 1, true)
      factory.node(rootId + 2, rootId, Some(rootId), "1-2", 1, true)

      go

      val root2Id = 2L << 32
      findNodes(root2Id) must beEqualTo(Seq(
        Node(root2Id + 0, root2Id, None, "root", 2, false),
        Node(root2Id + 1, root2Id, Some(root2Id), "1-1", 1, true),
        Node(root2Id + 2, root2Id, Some(root2Id), "1-2", 1, true)
      ))
    }
  }
}
