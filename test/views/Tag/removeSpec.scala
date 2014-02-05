package views.json.Tag

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

class removeSpec extends Specification with JsonMatchers {
  "Json for tag remove result" should {
    "contain removed count" in {
      views.json.Tag.remove(50L).toString must /("removed" -> 50L)
    }
  }
}
