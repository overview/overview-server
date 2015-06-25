package org.overviewproject.persistence

import org.overviewproject.test.DbSpecification

class TreeIdGeneratorSpec extends DbSpecification {
  "TreeIdGenerator" should {
    "generate first id if there are no trees" in new DbScope {
      val documentSet = factory.documentSet(id=123L)
      TreeIdGenerator.next(documentSet.id) must beEqualTo((123L << 32) | 1)
    }

    "generate the id following the largest found tree id" in new DbScope {
      val documentSet = factory.documentSet()
      val node = factory.node()
      factory.tree(id=((123L << 32) | 5), documentSetId=documentSet.id, rootNodeId=node.id)

      TreeIdGenerator.next(documentSet.id) must beEqualTo((123L << 32) | 6)
    }
  }
}
