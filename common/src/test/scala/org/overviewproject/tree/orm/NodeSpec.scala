package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator

class NodeSpec extends DbSpecification {
  step(setupDb)

  "Node" should {
    "write and read from the database" in new DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._

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

  step(shutdownDb)
}
