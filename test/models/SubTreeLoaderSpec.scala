package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SubTreeLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  trait MockComponents extends Scope {
    val loader = mock[SubTreeDataLoader]
    val parser = mock[SubTreeDataParser]
    val subTreeLoader = new SubTreeLoader(1, 4, loader, parser)
    val nodeIds = List(1l, 2l, 3l)
    
    val dummyNodes = nodeIds.map(core.Node(_, "dummyNode", Nil, null))
    val dummyDocuments = List(core.Document(10l, "documents", "created", "from data"))

  }
  
  "SubTreeLoader" should {
    
    "call loader and parser to create nodes" in new MockComponents {
      val nodeData = List((-1l, 1l, "root"), (1l, 2l, "child"))
      loader loadNodeData(1, 4) returns nodeData
      parser createNodes(nodeData, Nil) returns dummyNodes
      
      val nodes = subTreeLoader.loadNodes()
      
      there was one(loader).loadNodeData(1, 4)
      there was one(parser).createNodes(nodeData, Nil)
      
      nodes must be equalTo(dummyNodes)
    }
    
    "call loader and parser to create documents from nodes" in new MockComponents {
      val dummyDocumentData = List((10l, "actually", "all", "documentdata"))
      
      loader loadDocuments(nodeIds) returns dummyDocumentData
      parser createDocuments(dummyDocumentData) returns dummyDocuments
      
      val documents = subTreeLoader.loadDocuments(dummyNodes)

      there was one(loader).loadDocuments(nodeIds)
      there was one(parser).createDocuments(dummyDocumentData)
    }
    
    "not duplicate documents included in multiple nodes" in new MockComponents {
      val documentIds = List(10l, 20l, 30l, 10l, 40l, 10l, 40l, 40l, 50l)
      val dummyDocumentData = documentIds.map((_, "nodes will", "include the", "same documents"))
      
      val distinctDocumentData = documentIds.distinct.map((_, "nodes will", "include the", "same documents"))
          
      loader loadDocuments(nodeIds) returns dummyDocumentData
      parser createDocuments(distinctDocumentData) returns dummyDocuments 

      val documents = subTreeLoader.loadDocuments(dummyNodes)
      
      there was one(parser).createDocuments(distinctDocumentData)
    }
    
    "create documents in sorted order" in new MockComponents {
      val documentIds = List(30l, 20l, 40l, 10l)
      val dummyDocumentData = documentIds.map((_, "sort", "by", "documentId"))
      
      val sortedDocumentData = documentIds.sorted.map((_, "sort", "by", "documentId"))
      
      loader loadDocuments(nodeIds) returns dummyDocumentData
      parser createDocuments(sortedDocumentData) returns dummyDocuments
      
      val documents = subTreeLoader.loadDocuments(dummyNodes)
      
      there was one(parser).createDocuments(sortedDocumentData)
    }
    
    "be constructable with default loader and parser" in {
      val subTreeLoader = new SubTreeLoader(3, 55)
      
      success
    }
   
  }   
        
}