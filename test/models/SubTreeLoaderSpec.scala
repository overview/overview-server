package models

import org.specs2.mock._
import org.specs2.mutable.Specification


class SubTreeLoaderSpec extends Specification with Mockito {

  "SubTreeLoader" should {
    
    "call its loader and parser to create nodes" in {
      implicit val unusedConnection: java.sql.Connection = null
      
      val loader = mock[SubTreeDataLoader]
      val parser = mock[SubTreeDataParser]
      
      val nodeData = List((-1l, 1l, "root"), (1l, 2l, "child"))
      loader loadNodeData(1, 4) returns nodeData
      parser createNodes(nodeData, Nil) returns List(core.Node(1, "worked!", Nil, Nil))
      
      val subTreeLoader = new SubTreeLoader(1, 4, loader, parser)
      val nodes = subTreeLoader.loadNodes()
      
      there was one(loader).loadNodeData(1, 4)
      there was one(parser).createNodes(nodeData, Nil)
      
      nodes.head.description must be equalTo("worked!")
    }
    
    "be constructable with default loader and parser" in {
      val subTreeLoader = new SubTreeLoader(3, 55)
      
      success
    }
   
  }   
        
}