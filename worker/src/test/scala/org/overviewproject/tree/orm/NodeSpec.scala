package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.squeryl.PrimitiveTypeMode._

class NodeSpec extends DbSpecification {
  step(setupDb)

  "Node" should {
    
    "write and read from the database" in new DbTestContext {
      val documentSetId = insertDocumentSet("NodeSpec")
      val node = new Node(0, documentSetId, None, "description")
      
      Schema.nodes.insert(node)
      
      node.id must not be equalTo(0)
      
      val foundNode = Schema.nodes.lookup(node.id)
      foundNode must beSome
      foundNode must be equalTo Some(node)
    }
  }
  
  step(shutdownDb)
}