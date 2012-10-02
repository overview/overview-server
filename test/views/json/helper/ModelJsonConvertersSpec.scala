package views.json.helper

import models.core._
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson


class ModelJsonConvertersSpec extends Specification {
  
  "JsonDocumentIdList" should {
    import views.json.helper.ModelJsonConverters.JsonDocumentIdList
    
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
    import views.json.helper.ModelJsonConverters.JsonDocument
    
    "write document attributes" in {
      val id = 39l
      val title = "title"
      val document = Document(id, title, "unused by Tree", Seq(1, 2, 3))
      
      val documentJson = toJson(document).toString
      
      documentJson must /("id" -> id)
      documentJson must /("title" -> title)
      documentJson must contain(""""tagids":[1,2,3]""")
    }
  }
  
    
  "JsonTag" should {
    import views.json.helper.ModelJsonConverters.JsonTag
    
    "write tag attributes" in {
      val id = 5l
      val name = "tag"
      val documentCount = 22l
      val tag = Tag(id, name, None, DocumentIdList(Seq(10l), documentCount))
      
      val tagJson = toJson(tag).toString
      
      tagJson must /("id" -> id)
      tagJson must /("name" -> name)
      tagJson must /("doclist") */("n" -> documentCount)
    }
  }
}
