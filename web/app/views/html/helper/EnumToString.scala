package views.html.helper

import play.api.i18n.Messages

object EnumToString {
  def apply[T <: java.lang.Enum[T]](value: T)(implicit messages: Messages) = {
    Messages(value.getDeclaringClass().getName().replace("$", ".") + "." + value.toString())
  }
}
