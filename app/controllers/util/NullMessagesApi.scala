package controllers.util

import play.api.i18n.{Lang,Messages,MessagesApi}
import play.api.mvc.{RequestHeader,Result}

class NullMessagesApi(messages: () => Messages) extends MessagesApi {
  override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang) = (keys ++ args).mkString(",")
  override def apply(key: String, args: Any*)(implicit lang: Lang) = (Seq(key) ++ args).mkString(",")
  override def clearLang(result: Result) = result
  override def isDefinedAt(key: String)(implicit lang: Lang) = false
  override def langCookieHttpOnly = false
  override def langCookieName = ""
  override def langCookieSecure = false
  override def messages = Map()
  override def preferred(request: play.mvc.Http.RequestHeader) = messages()
  override def preferred(request: RequestHeader) = messages()
  override def preferred(candidates: Seq[Lang]) = messages()
  override def setLang(result: Result, lang: Lang) = result
  override def translate(key: String, args: Seq[Any])(implicit lang: Lang) = None
}

object NullMessagesApi {
  lazy val messagesApi: MessagesApi = new NullMessagesApi(() => messages)
  lazy val messages: Messages = new Messages(Lang("en"), messagesApi)
}
