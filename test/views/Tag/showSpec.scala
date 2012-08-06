package views.json.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class showSpec extends Specification {
  
  "Json for tag create result" should {
    
    "contain tagid and count" in {
      val tagId = 5l
      val count = 20l
      
      val resultJson = toJson(views.json.Tag.show((tagId, count))).toString 
      
      resultJson must /("id" -> tagId)
      resultJson must /("count" -> count)
    }
  }

}