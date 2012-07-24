package models

import org.specs2.mock._
import org.specs2.mutable.Specification


class SubTreeLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  "SubTreeLoader" should {
    
    "call loader and parser to create nodes" in {
      val loader = mock[SubTreeDataLoader]
      val parser = mock[SubTreeDataParser]

      val nodeData = List((-1l, 1l, "root"), (1l, 2l, "child"))
      loader loadNodeData(1, 4) returns nodeData
      parser createNodes(nodeData, Nil) returns List(core.Node(1, "worked!", Nil, null))
      
      val subTreeLoader = new SubTreeLoader(1, 4, loader, parser)
      val nodes = subTreeLoader.loadNodes()
      
      there was one(loader).loadNodeData(1, 4)
      there was one(parser).createNodes(nodeData, Nil)
      
      nodes.head.description must be equalTo("worked!")
    }
    
    "call loader and parser to create documents from nodes" in {
      val loader = mock[SubTreeDataLoader]
      val parser = mock[SubTreeDataParser]

      val documentIds = List(10l, 20l, 30l)
      val loadedNodes = List(core.Node(1, "dummyNode", Nil, null), 
    		  				 core.Node(2, "dummyNode", Nil, null), 
    		  				 core.Node(3, "dummyNode", Nil, null))
    		  				 
      val nodeIds = List(1l, 2l, 3l)
      
      val mockDocumentData = List((10l, "actually", "all", "documentdata"))
      val mockDocuments = List(core.Document(10l, "documents", "created", "from data"))
      
      loader loadDocuments(nodeIds) returns mockDocumentData
      parser createDocuments(mockDocumentData) returns mockDocuments
      
      val subTreeLoader = new SubTreeLoader(1, 4, loader, parser)
      val documents = subTreeLoader.loadDocuments(loadedNodes)

      there was one(loader).loadDocuments(nodeIds)
      there was one(parser).createDocuments(mockDocumentData)
    }
    
    "be constructable with default loader and parser" in {
      val subTreeLoader = new SubTreeLoader(3, 55)
      
      success
    }
   
  }   
        
}