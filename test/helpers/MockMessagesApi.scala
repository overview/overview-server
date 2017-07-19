package test.helpers

import java.util.Locale
import play.api.i18n.{Lang,Messages,MessagesApi}
import play.api.mvc.{RequestHeader,Result}

case class MockMessages(
  lang: Lang
) extends Messages {
  override def apply(keys: Seq[String], args: Any*) = (keys ++ args).mkString(",")
  override def apply(key: String, args: Any*) = (Seq(key) ++ args).mkString(",")
  override def isDefinedAt(key: String) = true
  override def translate(key: String, args: Seq[Any]) = Some(apply(key, args: _*))

  override def messages = this
}

object MockMessages {
  val defaultLocale = Locale.ROOT
  val defaultLang = Lang(defaultLocale)
  val default = MockMessages(defaultLang)
}

class MockMessagesApi extends MessagesApi {
  override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang) = (keys ++ args).mkString(",")
  override def apply(key: String, args: Any*)(implicit lang: Lang) = (Seq(key) ++ args).mkString(",")
  override def clearLang(result: Result) = result
  override def isDefinedAt(key: String)(implicit lang: Lang) = true
  override def langCookieHttpOnly = false
  override def langCookieName = ""
  override def langCookieSecure = false
  override def messages = Map()
  override def preferred(request: play.mvc.Http.RequestHeader) = {
    val candidates = request.acceptLanguages
    val lang = if (candidates.isEmpty) MockMessages.defaultLang else candidates.get(0)
    MockMessages(lang)
  }
  override def preferred(request: RequestHeader) = preferred(request.acceptLanguages)
  override def preferred(candidates: Seq[Lang]) = MockMessages(candidates.headOption.getOrElse(MockMessages.defaultLang))
  override def setLang(result: Result, lang: Lang) = result
  override def translate(key: String, args: Seq[Any])(implicit lang: Lang) = None
}
