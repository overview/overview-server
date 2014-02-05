package views.json.Tag

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

class addSpec extends Specification with JsonMatchers {
  "Json for tag add result" should {
    "contain added count" in {
      views.json.Tag.add(50L).toString must /("added" -> 50L)
    }
  }
}
