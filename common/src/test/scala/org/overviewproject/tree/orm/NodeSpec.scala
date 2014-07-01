package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator._
import org.overviewproject.postgres.SquerylEntrypoint._

class NodeSpec extends DbSpecification {
  step(setupDb)

  "Node" should {
    "write and read from the database" in new DbTestContext {
      val documentSet = Schema.documentSets.insertOrUpdate(DocumentSet(title = "NodeSpec"))
      val tree = Tree(nextTreeId(documentSet.id), documentSet.id, 0L, "tree", 100, "en", "", "")
      Schema.trees.insert(tree)
      val node = Node(
        id=nextNodeId(documentSet.id),
        treeId=tree.id,
        parentId=None,
        description="description",
        cachedSize=10,
        isLeaf=false
      )

      Schema.nodes.insert(node)

      node.id must not be equalTo(0)

      val foundNode = Schema.nodes.lookup(node.id)
      foundNode must beSome(node)
    }
  }

  step(shutdownDb)
}
