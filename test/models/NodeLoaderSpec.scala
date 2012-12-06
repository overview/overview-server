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

    "find the root Node" in new DbTestContext {
      val documentSetId = insertDocumentSet("SubTreeDataLoaderSpec")
      val root = Node(documentSetId, None, "root", 0, Array[Long]()).save
      
      val nodeLoader = new NodeLoader
      
      val node = nodeLoader.loadRoot(documentSetId)
      
      node must beSome.like { case n => n.id must be equalTo root.id }
    }
  }
  step(stop)
}