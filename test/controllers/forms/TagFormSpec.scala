package controllers.forms

import models.{OverviewTag, TagColor}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form

class TagFormSpec extends Specification {

  private val nameField = "name"
  private val colorField = "color"
  private val emptyFieldError = "error.required"
  private val invalidColorError = "color.invalid_format"

  private case class TestTag(id: Long, name: String) extends OverviewTag {
    def withName(newName: String) = TestTag(id, newName)
    def withColor(newColor: String) = new TestTag(id, name) with TagColor { val color = newColor }
    def withColor = None

    def save = this
    def delete {}
  }

  "TagForm" should {

    trait FormContext extends Scope {
      type TagForm = Form[OverviewTag with TagColor]

      val tag = TestTag(1l, "tag")
      
      def bindForm(name: String, color: String): TagForm =
        TagForm(tag).bind(Map(nameField -> name, colorField -> color))
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
      val updatedTag = tagForm.get

      updatedTag.name must be equalTo (name)
      updatedTag.color must be equalTo ("12abcc")
      updatedTag.id must be equalTo(tag.id)
    }

    "reject empty values" in new EmptyForm {
      val nameError = tagForm.error(nameField)
      val colorError = tagForm.error(colorField)

      nameError must beSome.like { case e => e.message must be equalTo (emptyFieldError) }
      colorError must beSome.like { case e => e.message must be equalTo (emptyFieldError) }
    }

    "reject non-hex color strings" in new NotValidHexColor {
      val colorError = tagForm.error(colorField)

      colorError must beSome.like { case e => e.message must be equalTo (invalidColorError) }
    }
  }
}
