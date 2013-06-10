package views.json.Tree

import models.core._
import org.specs2.mutable.Specification

import play.api.libs.json.Json.toJson
import models.orm.Tag

class showSpec extends Specification {
  
  "Tree view generated Json" should {
    
    "contain all nodes" in {
      val documentIds = DocumentIdList(List(10, 20, 30), 43)
      
      val nodes = List(
          Node(1l, "description", List(2, 3), documentIds, Map()),
          Node(2l, "description", List(4, 5, 6), documentIds, Map()), 
          Node(3l, "description", List(7), documentIds, Map()) 
      )
      
      val treeJson = show(nodes, Seq(), Seq(), Seq()).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }
    
    "contain all documents" in {
      val dummyNodes = List[Node]()
      val documents = List(
    	Document(10l, "description", Some("title"), Some("documentCloudId"), Seq(), Seq(22l)),
    	Document(20l, "description", Some("title"), Some("documentCloudId"), Seq(), Seq(22l)),
    	Document(30l, "description", Some("title"), Some("documentCloudId"), Seq(), Seq(22l))
      )

      val treeJson = show(dummyNodes, documents, Seq(), Seq()).toString
      
      treeJson must /("documents") */("id" -> 10l)
      treeJson must /("documents") */("id" -> 20l)
      treeJson must /("documents") */("id" -> 30l)
    }
    
    "contain tags" in {
      val dummyNodes = List[Node]()
      val dummyDocuments = List[Document]()

      val baseTag = Tag(id=5L, name="tag1", documentSetId=1L, color=Some("ffffff"))

      val tags = List[(Tag,Long)](
        (baseTag.copy(id=5L, name="tag1"), 5L),
        (baseTag.copy(id=15L, name="tag2"), 10L)
      )
      val treeJson = show(dummyNodes, dummyDocuments, tags, Seq()).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }
  }
  
  "JsonNode" should {
    import views.json.Tree.show.JsonNode
    
    "write node attributes" in {
      val documentIds = DocumentIdList(List(10, 20, 30), 45)
      val tagCounts = Map(("3" -> 22l), ("4" -> 555l))
      val node = Node(1, "node", List(4, 5, 6), documentIds, tagCounts)
      
      val nodeJson = toJson(node).toString
      
      nodeJson must /("id" -> 1)
      nodeJson must /("description" -> "node")
      nodeJson must contain("\"children\":" + List(4, 5, 6).mkString("[", ",", "]"))
      nodeJson must =~ ("doclist.*docids.*n".r)
      
      nodeJson must contain(""""tagcounts":{"3":22,"4":555}""")
    }
  }

}
