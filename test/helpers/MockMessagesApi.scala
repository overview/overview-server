package test.helpers

import play.api.i18n.{Lang,Messages,MessagesApi}
import play.api.mvc.{RequestHeader,Result}

class MockMessagesApi extends MessagesApi {
  override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang) = (keys ++ args.map(_.toString)).mkString(",")
  override def apply(key: String, args: Any*)(implicit lang: Lang) = (Seq(key) ++ args.map(_.toString)).mkString(",")
  override def clearLang(result: Result) = result
  override def isDefinedAt(key: String)(implicit lang: Lang) = true
  override def langCookieHttpOnly = true
  override def langCookieName = "langCookieName"
  override def langCookieSecure = true
  override def messages = Map()
  override def preferred(request: play.mvc.Http.RequestHeader) = new Messages(Lang("en"), this)
  override def preferred(request: RequestHeader) = new Messages(Lang("en"), this)
  override def preferred(candidates: Seq[Lang]) = new Messages(Lang("en"), this)
  override def setLang(result: Result, lang: Lang) = result
  override def translate(key: String, args: Seq[Any])(implicit lang: Lang) = Some(apply(key, args: _*))
}
