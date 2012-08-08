package views.json.Tree

import org.specs2.mutable.Specification
import models.core._

import play.api.libs.json.Json.toJson

class showSpec extends Specification {
  
  "Tree view generated Json" should {
    
    "contain all nodes" in {
      val documentIds = DocumentIdList(List(10, 20, 30), 43)
      
      val nodes = List(
          Node(1l, "description", List(2, 3), documentIds, Map()),
          Node(2l, "description", List(4, 5, 6), documentIds, Map()), 
          Node(3l, "description", List(7), documentIds, Map()) 
      )
      
      val dummyDocuments = List[Document]()
      
      val treeJson = show(nodes, dummyDocuments).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }
    
    "contain all documents" in {
      val dummyNodes = List[Node]()
      val documents = List(
    	Document(10l, "title", "textUrl", "viewUrl"),
    	Document(20l, "title", "textUrl", "viewUrl"),
    	Document(30l, "title", "textUrl", "viewUrl")
      )
      
      val treeJson = show(dummyNodes, documents).toString
      
      treeJson must /("documents") */("id" -> 10l)
      treeJson must /("documents") */("id" -> 20l)
      treeJson must /("documents") */("id" -> 30l)
    }
    
    "contain empty tags because they're not implemented yet" in {
      val dummyNodes = List[Node]()
      val dummyDocuments = List[Document]()
      
      val treeJson = show(dummyNodes, dummyDocuments).toString
      
      treeJson must contain("\"tags\":[]")
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
  
  "JsonDocumentIdList" should {
    import views.json.Tree.show.JsonDocumentIdList
    
    "write documentIdList attributes" in {
      val ids = List(10l, 20l, 34l)
      val count = 45l
      val documentIdList = DocumentIdList(ids, count)
      
      val documentIdListJson = toJson(documentIdList).toString
      
      documentIdListJson must contain("\"docids\":" + ids.mkString("[", ",", "]"))
      documentIdListJson must /("n" -> count)
    }
  }
  
  "JsonDocument" should {
    import views.json.Tree.show.JsonDocument
    
    "write document attributes" in {
      val id = 39l
      val title = "title"
      val document = Document(id, title, "unused by Tree", "unused by Tree", Seq(1, 2, 3))
      
      val documentJson = toJson(document).toString
      
      documentJson must /("id" -> id)
      documentJson must /("description" -> title)
      documentJson must contain(""""tagids":[1,2,3]""")
    }
  }

}
