package test.helpers

import java.util.Locale
import play.api.i18n.{DefaultLangs,DefaultMessagesApi,Lang,Messages,MessagesApi}
import play.api.mvc.{RequestHeader,Result}

class MockMessagesApi extends DefaultMessagesApi(
  messages=Map.empty[String, Map[String, String]],
  langs=new DefaultLangs(Seq(Lang(Locale.ROOT)))
) {
  override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang) = (keys ++ args).mkString(",")
  override def apply(key: String, args: Any*)(implicit lang: Lang) = (Seq(key) ++ args).mkString(",")
  override def isDefinedAt(key: String)(implicit lang: Lang) = true
}

object MockMessages {
  val defaultLocale = Locale.ROOT
  val defaultLang = Lang(defaultLocale)
  val defaultApi = new MockMessagesApi()
  val default = defaultApi.preferred(Seq(defaultLang))
}
