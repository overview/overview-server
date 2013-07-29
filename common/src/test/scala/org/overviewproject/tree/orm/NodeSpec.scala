package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.IdGenerator._
import org.overviewproject.postgres.SquerylEntrypoint._

class NodeSpec extends DbSpecification {
  step(setupDb)

  "Node" should {
    inExample("write and read from the database") in new DbTestContext {
      val documentSetId = insertDocumentSet("NodeSpec")
      val node = Node(
        id=nextNodeId(documentSetId),
        documentSetId=documentSetId,
        parentId=None,
        description="description",
        cachedSize=10,
        cachedDocumentIds=Array[Long](1, 2, 3, 4, 5),
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
