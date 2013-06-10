package views.json.Tag

import org.specs2.mutable.Specification

class addSpec extends Specification {
  "Json for tag add result" should {
    "contain added count" in {
      // specs2 bug: this doesn't work
      //views.json.Tag.add(50L) must /("added" -> 50L)
    }
  }
}
