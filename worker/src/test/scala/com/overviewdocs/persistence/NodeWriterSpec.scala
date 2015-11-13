package com.overviewdocs.persistence

import com.overviewdocs.models.{Node,NodeDocument}
import com.overviewdocs.models.tables.{Nodes,NodeDocuments}
import com.overviewdocs.test.DbSpecification

class NodeWriterSpec extends DbSpecification {
  "NodeWriter" should {
    "not flush immediately" in new DbScope {
      val writer = new NodeWriter
      writer.blockingCreateAndFlushIfNeeded(1L, 1L, None, "root", true, Seq())

      import database.api._
      blockingDatabase.seq(Nodes) must beEmpty
    }

    "write Nodes" in new DbScope {
      val writer = new NodeWriter
      writer.blockingCreateAndFlushIfNeeded(1L, 1L, None, "root", false, Seq())
      writer.blockingCreateAndFlushIfNeeded(2L, 1L, Some(1L), "leaf", true, Seq())
      writer.blockingFlush

      import database.api._
      blockingDatabase.seq(Nodes.sortBy(_.id)) must beEqualTo(Seq(
        Node(1L, 1L, None, "root", 0, false),
        Node(2L, 1L, Some(1L), "leaf", 0, true)
      ))
    }

    "write NodeDocuments and counts" in new DbScope {
      val documentSet = factory.documentSet()
      val document1 = factory.document(documentSetId=documentSet.id)
      val document2 = factory.document(documentSetId=documentSet.id)

      val writer = new NodeWriter
      writer.blockingCreateAndFlushIfNeeded(1L, 1L, None, "root", false, Seq(document1.id, document2.id))
      writer.blockingCreateAndFlushIfNeeded(2L, 1L, Some(1L), "leaf", true, Seq(document1.id))
      writer.blockingCreateAndFlushIfNeeded(3L, 1L, Some(1L), "leaf", true, Seq(document2.id))
      writer.blockingFlush

      import database.api._
      blockingDatabase.seq(Nodes.sortBy(_.id)).map(_.cachedSize) must beEqualTo(Seq(2, 1, 1))
      blockingDatabase.seq(NodeDocuments).toSet must beEqualTo(Set(
        NodeDocument(1L, document1.id),
        NodeDocument(1L, document2.id),
        NodeDocument(2L, document1.id),
        NodeDocument(3L, document2.id)
      ))
    }
  }
}
