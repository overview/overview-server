package controllers.forms

import play.api.data.{Form,Forms}
import play.api.data.validation.Constraints

object ViewForm {
  private def url = Forms.nonEmptyText.verifying(Constraints.pattern("""^(https?:)?//.*""".r))

  case class Attributes(
    title: String,
    url: String,
    serverUrlFromPlugin: Option[String]
  )

  def create(email: String): Form[Attributes] = Form(
    Forms.mapping(
      "title" -> Forms.nonEmptyText,
      "url" -> url,
      "serverUrlFromPlugin" -> Forms.optional(url)
    )(Attributes.apply)(Attributes.unapply)
  )
}
