package com.overviewdocs.database

import com.overviewdocs.models.NodeDocument
import com.overviewdocs.models.tables.{DanglingNodes,NodeDocuments,Nodes}
import com.overviewdocs.test.DbSpecification

class DanglingNodeDeleterSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    protected implicit val ec = database.executionContext
    def go: Unit = await(DanglingNodeDeleter.run)
  }

  "DanglingNodeDeleter" should {
    "delete root Nodes" in new BaseScope {
      val node = factory.node()
      factory.danglingNode(node.id)
      go
      blockingDatabase.option(Nodes) must beNone
    }

    "not delete non-dangling Nodes" in new BaseScope {
      import database.api._

      val node1 = factory.node()
      val node2 = factory.node()
      factory.danglingNode(node1.id)
      go
      blockingDatabase.option(Nodes.filter(_.id === node2.id)) must beSome
    }

    "delete child Nodes" in new BaseScope {
      import database.api._

      val rootNode = factory.node()
      val childNode = factory.node(parentId=Some(rootNode.id), rootId=rootNode.id)
      factory.danglingNode(rootNode.id)
      go
      blockingDatabase.option(Nodes) must beNone
    }

    "not delete children of non-dangling Nodes" in new BaseScope {
      import database.api._

      val rootNode1 = factory.node()
      val childNode1 = factory.node(parentId=Some(rootNode1.id), rootId=rootNode1.id)
      val rootNode2 = factory.node()
      val childNode2 = factory.node(parentId=Some(rootNode2.id), rootId=rootNode2.id)
      factory.danglingNode(rootNode1.id)
      go
      blockingDatabase.option(Nodes.filter(_.id === childNode2.id)) must beSome
    }

    "delete NodeDocuments" in new BaseScope {
      import database.api._

      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id)
      val rootNode = factory.node()
      val childNode = factory.node(parentId=Some(rootNode.id), rootId=rootNode.id)
      factory.nodeDocument(childNode.id, document.id)
      factory.danglingNode(rootNode.id)
      go
      blockingDatabase.option(NodeDocuments) must beNone
    }

    "not delete NodeDocuments of non-dangling Nodes" in new BaseScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id)
      val node1 = factory.node()
      val node2 = factory.node() // root of a separate Tree
      factory.nodeDocument(node1.id, document.id)
      factory.nodeDocument(node2.id, document.id)
      factory.danglingNode(node1.id)
      go
      blockingDatabase.seq(NodeDocuments) must beEqualTo(Seq(NodeDocument(node2.id, document.id)))
    }

    "delete the DanglingNode" in new BaseScope {
      factory.danglingNode(factory.node().id)
      go
      blockingDatabase.option(DanglingNodes) must beNone
    }
  }
}
