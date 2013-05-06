package controllers.forms

import play.api.data.validation._

package object validation {
  def minLengthPassword(length: Int): Constraint[String] = Constraint[String]("constraint.passwordMinLength", length) { o =>
    if (o.size >= length) Valid else Invalid(ValidationError("password.secure", length))
  }
}
