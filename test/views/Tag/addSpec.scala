package views.json.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class addSpec extends Specification {
  
  "Json for tag add result" should {
    
    "contain tagid, added count, and total count" in {
      val tagId = 5l
      val addedCount = 20l
      val totalCount = 44l
      
      val resultJson = toJson(views.json.Tag.add(tagId, addedCount, totalCount)).toString 
      
      resultJson must /("id" -> tagId)
      resultJson must /("numAdded" -> addedCount)
      resultJson must /("numTotal" -> totalCount)
    }
  }

}