package controllers.forms

import play.api.data.validation._

package object validation {
  def minLengthPassword(length: Int): Constraint[String] = Constraint[String]("constraint.passwordMinLength", length) { o =>
    if (o.size >= length) Valid else Invalid(ValidationError("password.secure", length))
  }
  
  def supportedLang: Constraint[String] = Constraint[String]("constraint.supportedLang") { l =>
    val langs = Seq(
      "de",
      "en",
      "es",
      "fr",
      "sv"
    )
   
    if (langs.contains(l)) Valid
    else Invalid(ValidationError("forms.validation.unsupportedLanguage", l))
  }
}
