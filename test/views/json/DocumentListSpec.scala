package views.json

import models.core.Document
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class DocumentListSpec extends Specification {

  "DocumentList view generated Json" should {
    
    "contain documents and total_items" in {
      val documents =  List(
          Document(10, "title1", "textUrl1", "viewUrl1"),
          Document(20, "title2", "textUrl2", "viewUrl2"),
          Document(30, "title3", "textUrl3", "viewUrl3")
      )
      val totalCount = 13l
      
      val documentListJson = DocumentList.show(documents, totalCount).toString
      
      documentListJson must beMatching(".*\"documents\":\\[(.*title.*,?){3}\\].*".r)
      documentListJson must /("total_items" -> totalCount)
    }
  }
  
  "JsonDocument" should {
    import views.json.DocumentList.JsonDocument
    
    "write document id and title" in {
      val document = Document(10l, "document title", "unused", "unused")
      
      val documentJson = toJson(document).toString
      
      documentJson must /("id" -> 10l)
      documentJson must /("title" -> "document title")
    }
    
    "write empty tag id list until implemented" in {
      val document = Document(10l, "document title", "unused", "unused")
      
      val documentJson = toJson(document).toString
      
      documentJson must contain("\"tagids\":[]")
    }
  }
}