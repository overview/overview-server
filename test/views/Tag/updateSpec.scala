package views.json.Tag

import helpers.TestTag
import models.core.DocumentIdList
import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo

class updateSpec extends Specification {

  "Json for update tag result" should {

    "contain tag attributed" in {
      val id = 34l
      val name = "tag"
      val color = "aabbcc"
      val colorForJs = '#' + color
      val documentCount = 233l
      val docList = DocumentIdList(Seq(10l), documentCount)

      val tag = TestTag(id, name, Some(color), docList)

      val tagJson = toJson(views.json.Tag.update(tag)).toString
      tagJson must /("id" -> id)
      tagJson must /("name" -> name)
      tagJson must /("color" -> colorForJs)
      tagJson must /("doclist") */ ("n" -> documentCount)
    }
  }

}

