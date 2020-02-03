package controllers.util

import java.util.Locale
import play.api.i18n.{DefaultLangs,DefaultMessagesApi,Lang,Langs,Messages,MessagesApi,MessagesImpl}
import play.api.mvc.{RequestHeader,Result}

/*
class NullMessagesApi(messages: () => Messages) extends MessagesApi {
  override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang) = (keys ++ args).mkString(",")
  override def apply(key: String, args: Any*)(implicit lang: Lang) = (Seq(key) ++ args).mkString(",")
  override def clearLang(result: Result) = result
  override def isDefinedAt(key: String)(implicit lang: Lang) = false
  override def langCookieHttpOnly = false
  override def langCookieName = ""
  override def langCookieSameSite = None
  override def langCookieSecure = false
  override def messages = Map()
  override def preferred(request: play.mvc.Http.RequestHeader) = {
    val candidates = request.acceptLanguages
    val lang = if (candidates.isEmpty) {
      defaultLang
    } else {
      candidates.get(0)
    }
    NullMessages(lang)
  }
  override def preferred(request: RequestHeader) = preferred(request.acceptLanguages)
  override def preferred(candidates: Seq[Lang]) = NullMessages(candidates.headOption.getOrElse(defaultLang))
  override def setLang(result: Result, lang: Lang) = result
  override def translate(key: String, args: Seq[Any])(implicit lang: Lang) = None

  private val defaultLocale = Locale.ROOT
  private val defaultLang = Lang(defaultLocale)
}
*/

object NullMessagesApi {
  val lang: Lang = Lang(Locale.ROOT)
  val langs: Langs = new DefaultLangs(Seq(lang))
  val messagesApi: MessagesApi = new DefaultMessagesApi(Map.empty[String,Map[String,String]], langs)
  val messages: Messages = messagesApi.preferred(Seq(lang))
}
