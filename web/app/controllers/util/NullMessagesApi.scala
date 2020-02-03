package controllers.util

import java.util.Locale
import play.api.i18n.{DefaultLangs,DefaultMessagesApi,Lang,Langs,Messages,MessagesApi}

/** MessagesApi whose methods should never be called.
  *
  * Used for dependency injection.
  */
object NullMessagesApi {
  val lang: Lang = Lang(Locale.ROOT)
  val langs: Langs = new DefaultLangs(Seq(lang))
  val messagesApi: MessagesApi = new DefaultMessagesApi(Map.empty[String,Map[String,String]], langs)
  val messages: Messages = messagesApi.preferred(Seq(lang))
}
