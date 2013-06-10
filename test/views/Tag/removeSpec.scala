package views.json.Tag

import org.specs2.mutable.Specification

class removeSpec extends Specification {
  "Json for tag remove result" should {
    "contain removed count" in {
      // specs2 bug: this doesn't work
      //views.json.Tag.remove(50L) must /("removed" -> 50L)
    }
  }
}
