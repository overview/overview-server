package views.json.helper

import models.PersistentTagInfo
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
      val document = Document(id, title, "unused by Tree", Seq(1, 2, 3), Seq(22l, 11l, 33l))

      val documentJson = toJson(document).toString

      documentJson must /("id" -> id)
      documentJson must /("title" -> title)
      documentJson must contain(""""tagids":[1,2,3]""")
      documentJson must contain(""""nodeids":[22,11,33]""")
    }
  }

  "JsonTag" should {
    import views.json.helper.ModelJsonConverters.JsonTag

    "write tag attributes" in {
      val id = 5l
      val name = "tag"
      val color = "013370"
      val colorForJs = "#" + color
      val documentCount = 22l
      val tag = Tag(id, name, Some(color), DocumentIdList(Seq(10l), documentCount))

      val tagJson = toJson(tag).toString

      tagJson must /("id" -> id)
      tagJson must /("name" -> name)
      tagJson must /("color" -> colorForJs)
      tagJson must /("doclist") */ ("n" -> documentCount)
    }

    "omit color value if color is not set" in {
      val noColor = None
      val tag = Tag(0, "", noColor, DocumentIdList(Seq(10l), 4))

      val tagJson = toJson(tag).toString

      tagJson must not / ("color")
    }
  }

  "JsonPersistentTag" should {
    import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo
    import helpers.TestTag
    
    "write tag attributes" in {
      val documentCount = 5l
      val tag: PersistentTagInfo =
	TestTag(44l, "a tag", Some("e1e100"), DocumentIdList(Seq(10l), documentCount))

      val colorForJs = "#" + tag.color.get
      
      val tagJson = toJson(tag).toString
      tagJson must /("id" -> tag.id)
      tagJson must /("name" -> tag.name)
      tagJson must /("color" -> colorForJs)
      tagJson must /("doclist") */ ("n" -> documentCount)
    }

    "omit color value if color is not set" in {
      val noColor = None
      val tag: PersistentTagInfo = TestTag(0, "", noColor, DocumentIdList(Seq(10l), 4))

      val tagJson = toJson(tag).toString

      tagJson must not / ("color")
    }
  
  }
}
