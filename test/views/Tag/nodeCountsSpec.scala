package views.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class nodeCountsSpec extends Specification {
  
  "Json for update node count result" should {
    
    "contain counts in an array" in {
      val nodeCounts = Seq((1l, 45), (2l, 33), (3l, 0))
      
      val nodeCountArray = toJson(views.json.Tag.nodeCounts(nodeCounts)).toString
      
      nodeCountArray must beEqualTo("[1,45,2,33,3,0]")
    }
  }

}
