package views.html.helper

import play.api.i18n.{Lang,Messages}

import org.overviewproject.tree.orm.DocumentSetCreationJob

object EnumToString {
  def apply[T <: java.lang.Enum[T]](value: T)(implicit lang: Lang) = {
    Messages(value.getDeclaringClass().getName().replace("$", ".") + "." + value.toString())
  }
}
