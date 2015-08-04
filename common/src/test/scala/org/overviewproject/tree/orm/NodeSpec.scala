package com.overviewdocs.tree.orm

import com.overviewdocs.test.DbSpecification
import com.overviewdocs.test.IdGenerator

class NodeSpec extends DbSpecification {
  "Node" should {
    "write and read from the database" in new DbTestContext {
      import com.overviewdocs.postgres.SquerylEntrypoint._

      val id = IdGenerator.nextNodeId(1L)
      val node = Node(
        id=id,
        rootId=id,
        parentId=None,
        description="description",
        cachedSize=10,
        isLeaf=false
      )

      Schema.nodes.insert(node)

      Schema.nodes.lookup(node.id) must beSome(node)
    }
  }
}
