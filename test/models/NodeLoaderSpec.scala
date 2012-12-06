package models

import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.Specification
import helpers.DbTestContext
import org.overviewproject.tree.orm.Node

class NodeLoaderSpec extends Specification {
  step(start(FakeApplication()))

  "NodeLoader" should {

    trait NodeContext extends DbTestContext {
      var documentSetId: Long = _
      var root: Node = _

      val nodeLoader = new NodeLoader

      override def setupWithDb = {
        documentSetId = insertDocumentSet("SubTreeDataLoaderSpec")
        root = Node(documentSetId, None, "root", 0, Array[Long]()).save
      }
    }

    trait SmallTreeContext extends NodeContext {
      var nodeIds: Seq[Long] = _

      override def setupWithDb = {
        super.setupWithDb
        nodeIds = root.id +: createNextLevel(root.id)
      }

      protected def createNextLevel(nodeId: Long): Seq[Long] = {
        for (i <- 1 to 2) yield {
          val c = Node(documentSetId, Some(nodeId), "child", 0, Array[Long]()).save
          c.id
        }
      }
    }

    trait LargerTreeContext extends SmallTreeContext {
      override def setupWithDb = {
        super.setupWithDb

        val level2 = nodeIds.tail.flatMap(createNextLevel)
        val level3 = level2.flatMap(createNextLevel)
        nodeIds ++= level2 ++ level3
      }
    }

    "find the root Node" in new NodeContext {
      val node = nodeLoader.loadRoot(documentSetId)

      node must beSome.like { case n => n.id must be equalTo root.id }
    }

    "load a small tree" in new SmallTreeContext {
      val nodes = nodeLoader.loadTree(documentSetId, root, 1)
      nodes.map(_.id) must haveTheSameElementsAs(nodeIds)
    }

    "should handle too much depth" in new SmallTreeContext {
      val nodes = nodeLoader.loadTree(documentSetId, root, 10)
      nodes.map(_.id) must haveTheSameElementsAs(nodeIds)
    }

    "return root if depth is 0" in new SmallTreeContext {
      val nodes = nodeLoader.loadTree(documentSetId, root, 0)
      nodes.map(_.id) must contain(root.id).only
    }

    "handle multiple levels" in new LargerTreeContext {
      val nodes = nodeLoader.loadTree(documentSetId, root, 2)
      nodes.map(_.id) must haveTheSameElementsAs(nodeIds.take(7))
    }
  }
  step(stop)
}