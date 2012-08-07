package views.Tag

import org.specs2.mutable.Specification
import play.api.libs.json.Json.toJson

class updateNodeCountsSpec extends Specification {
  
  "Json for update node count result" should {
    
    "contain counts in an array" in {
      val nodeCounts = Seq((1l, 45l), (2l, 33l), (3l, 0l))
      
      val nodeCountArray = toJson(views.json.Tag.updateNodeCounts(nodeCounts)).toString
      
      nodeCountArray must beEqualTo("[1,45,2,33,3,0]")
    }
  }

}