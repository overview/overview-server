package views.json.helper

import play.api.libs.json.Json.toJson
import org.overviewproject.tree.orm.DocumentProcessingError
import org.specs2.specification.Scope

import org.overviewproject.test.DbSpecification
import models.PersistentTagInfo
import models.core._

class ModelJsonConvertersSpec extends DbSpecification {

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
