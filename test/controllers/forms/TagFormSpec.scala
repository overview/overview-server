package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class TagFormSpec extends Specification {

  "TagForm" should {

    trait ValidForm extends Scope {
      val name = "tagName"
      val color = "#12abcc"
      
      val tagForm = TagForm().bind(Map("name" -> name, "color" -> color))
    }

    trait EmptyForm extends Scope {
      val tagForm = TagForm().bind(Map("name" -> "", "color" -> ""))
    }
    
    "extract name and color" in new ValidForm {
      val (nameValue, colorValue) = tagForm.get
      
      nameValue must be equalTo(name)
      colorValue must be equalTo(color)
    }

    "reject empty values" in new EmptyForm {
      val nameError = tagForm.error("name")
      val colorError = tagForm.error("color")

      nameError must beSome.like { case e => e.message must be equalTo("error.required") }
      colorError must beSome.like { case e => e.message must be equalTo("error.required") }

    }
  }
}
