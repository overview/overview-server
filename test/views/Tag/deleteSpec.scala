package views.json.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class deleteSpec extends Specification {

  "Json for delete tag result" should {

    "contain tag id and name" in {
      val tagId = 43l
      val tagName = "tagname"
      
      val resultJson = toJson(views.json.Tag.delete(tagId, tagName)).toString

      resultJson must /("id" -> tagId)
      resultJson must /("name" -> tagName)

    }

  }
}
