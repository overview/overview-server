package controllers.forms

import play.api.data.{Form,Forms,Mapping}

import org.overviewproject.models.Tag

object TagForm {
  /** Parses "#abc123" as "abc123". */
  private val colorMapping: Mapping[String] = {
    val format = "^#[0-9a-fA-F]{6}$"
    Forms.nonEmptyText
      .verifying("color.invalid_format", _.matches(format))
      .transform(_.substring(1), "#" + _)
  }

  /** Parses name+color from the form. */
  private val tupleMapping: Mapping[(String,String)] = Forms.tuple(
    "name" -> Forms.nonEmptyText,
    "color" -> colorMapping
  )

  private def mapping[A](apply: (String,String) => A, unapply: A => Option[(String,String)]): Mapping[A] = {
    tupleMapping.transform(apply.tupled, unapply(_).get)
  }

  val forCreate: Form[Tag.CreateAttributes] = {
    Form(mapping(Tag.CreateAttributes.apply _, Tag.CreateAttributes.unapply _))
  }

  val forUpdate: Form[Tag.UpdateAttributes] = {
    Form(mapping(Tag.UpdateAttributes.apply _, Tag.UpdateAttributes.unapply _))
  }
}
