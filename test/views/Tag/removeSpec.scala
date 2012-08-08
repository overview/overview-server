package views.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson


class removeSpec extends Specification {
 
  "Json for tag remove result" should {
    
    "contain tagid, removed count, and total count" in {
      val tagId = 5l
      val removedCount = 20l
      val totalCount = 24l
      
      val resultJson = toJson(views.json.Tag.remove(tagId, removedCount, totalCount)).toString 
      
      resultJson must /("id" -> tagId)
      resultJson must /("numRemoved" -> removedCount)
      resultJson must /("numTotal" -> totalCount)
      
      
    }
  }

}