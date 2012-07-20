package models

import org.specs2.mutable.Specification

class SubTreeDataParserSpec extends Specification {
  
  "SubTreeDataParser" should {
    
    "create Nodes from tuples" in {
      
      val nodeData = List(
          (-1l, 1l, "root"),
          (1l, 2l, "child1-1"), (1l, 3l, "child1-2"),
          (2l, 4l, "child2-1"), (2l, 5l, "child2-2"), (2l, 6l, "child2-3"), (2l, 7l, "child2-4"),
          (3l, 8l, "child3-1"), (3l, 9l, "child3-2"), (3l, 10l, "child3-3")
      )
      
      val subTreeDataParser = new SubTreeDataParser()
      
      val nodes = subTreeDataParser.createNodes(nodeData)
      
      nodes must have size(10)
      
      val nodeDescriptions = nodeData.map(n => (n._2, n._3))
      
      nodes.map(n => (n.id, n.description)) must haveTheSameElementsAs(nodeDescriptions)
    }
  }

}