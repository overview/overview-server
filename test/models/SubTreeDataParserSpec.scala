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
      
      val root = nodes.find(_.id == 1l).get
      val child2 = nodes.find(_.id == 2l).get
      val child3 = nodes.find(_.id == 3l).get
      
      root.childNodeIds   must haveTheSameElementsAs(List(2l, 3l))
      child2.childNodeIds must haveTheSameElementsAs(List(4l, 5l, 6l, 7l))
      child3.childNodeIds must haveTheSameElementsAs(List(8l, 9l, 10l))
      
      val leafIds = 4l to 10l
      val leafNodes = leafIds.flatMap(i => nodes.find(_.id == i))
      
      leafNodes.flatMap(_.childNodeIds) must be empty
       
    }
  }

}