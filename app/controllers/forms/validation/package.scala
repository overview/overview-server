package controllers.forms

import play.api.data.validation._

import org.overviewproject.util.SupportedLanguages

package object validation {
  def minLengthPassword(length: Int): Constraint[String] = Constraint[String]("constraint.passwordMinLength", length) { o =>
    if (o.size >= length) Valid else Invalid(ValidationError("password.secure", length))
  }
  
  def supportedLang: Constraint[String] = Constraint[String]("constraint.supportedLang") { languageCode =>
    if (SupportedLanguages.languageCodes.contains(languageCode)) {
      Valid
    } else {
      Invalid(ValidationError("forms.validation.unsupportedLanguage", languageCode))
    }
  }
}
