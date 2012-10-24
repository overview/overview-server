package views.json.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class createSpec extends Specification {

  "Json for create tag result" should {

    "contain tag id and name" in {
      val tagId = 43l
      val tagName = "tagname"
      
      val resultJson = toJson(views.json.Tag.create(tagId, tagName)).toString

      resultJson must /("id" -> tagId)
      resultJson must /("name" -> tagName)

    }

  }
}
