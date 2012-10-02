package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form

class TagFormSpec extends Specification {

  private val nameField = "name"
  private val colorField = "color"
  private val emptyFieldError = "error.required"
  private val invalidColorError = "color.invalid_format"
  
  "TagForm" should {

    trait FormContext extends Scope {
      type TagForm = Form[(String, String)]
      
      def bindForm(name: String, color: String): TagForm =
        TagForm().bind(Map(nameField -> name, colorField -> color))
    }
    
    trait ValidForm extends FormContext {
      val name = "tagName"
      val color = "#12aBcc"
      
      val tagForm = bindForm(name, color)
    }

    trait EmptyForm extends FormContext {
      val tagForm = bindForm("", "")
    }

    trait NotValidHexColor extends FormContext {
      val tagForm = bindForm("name", "not a color")
    }
    
    "extract name and color" in new ValidForm {
      val (nameValue, colorValue) = tagForm.get
      
      nameValue must be equalTo(name)
      colorValue must be equalTo("12abcc")
    }

    
    "reject empty values" in new EmptyForm {
      val nameError = tagForm.error(nameField)
      val colorError = tagForm.error(colorField)

      nameError must beSome.like { case e => e.message must be equalTo(emptyFieldError) }
      colorError must beSome.like { case e => e.message must be equalTo(emptyFieldError) }
    }

    "reject non-hex color strings" in new NotValidHexColor {
      val colorError = tagForm.error(colorField)

      colorError must beSome.like { case e => e.message must be equalTo(invalidColorError) }
    }
  }
}
