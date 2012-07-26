package views.json

import org.specs2.mutable.Specification
import models.core._

import play.api.libs.json.Json.toJson

class TreeSpec extends Specification {
  
  "Tree view generated Json" should {
    
    "contain all nodes" in {
      val documentIds = DocumentIdList(List(10, 20, 30), 43)
      
      val nodes = List(
          Node(1, "description", List(2, 3), documentIds),
          Node(2, "description", List(4, 5, 6), documentIds), 
          Node(3, "description", List(7), documentIds) 
      )
      
      val treeJson = ATree.show(nodes).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }
  }
  
  "JsonNode" should {
    import views.json.ATree.JsonNode
    
    "write node attributes" in {
      val documentIds = DocumentIdList(List(10, 20, 30), 45)
      val node = Node(1, "node", List(4, 5, 6), documentIds)
      
      val nodeJson = toJson(node).toString
      
      nodeJson must /("id" -> 1)
      nodeJson must /("description" -> "node")
      nodeJson must contain("\"children\":" + List(4, 5, 6).mkString("[", ",", "]"))
      nodeJson must =~ ("doclist.*docids.*n".r)
    }
  }
  
  "JsonDocumentIdList" should {
    import views.json.ATree.JsonDocumentIdList
    
    "write documentIdList attributes" in {
      val ids = List(10l, 20l, 34l)
      val count = 45l
      val documentIdList = DocumentIdList(ids, count)
      
      val documentIdListJson = toJson(documentIdList).toString
      
      documentIdListJson must contain("\"docids\":" + ids.mkString("[", ",", "]"))
      documentIdListJson must /("n" -> count)
    }
  }

}