package controllers.forms

import org.specs2.mutable.Specification

class TagFormSpec extends Specification {

  "TagForm" should {

    "extract name and color" in {
      val name = "tagName"
      val color = "#12abcc"
      
      val tagForm = TagForm().bind(Map("name" -> name, "color" -> color))
      
      val (nameValue, colorValue) = tagForm.get
      
      nameValue must be equalTo(name)
      colorValue must be equalTo(color)
    }

  }
}
