package controllers.forms

import play.api.data.{Form,Forms}
import play.api.data.validation.Constraints

object VizForm {
  case class Attributes(
    title: String,
    url: String
  )

  def create(email: String): Form[Attributes] = Form(
    Forms.mapping(
      "title" -> Forms.nonEmptyText,
      "url" -> Forms.nonEmptyText.verifying(Constraints.pattern("""^https?://.*""".r))
    )(Attributes.apply)(Attributes.unapply)
  )
}
