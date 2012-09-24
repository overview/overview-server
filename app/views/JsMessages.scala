package views

import play.api.Play.current
import play.api.libs.json.{JsObject,JsString}
import play.api.i18n.{Lang,Messages}

object JsMessages {
  def apply(keys: Seq[String])(implicit lang: Lang) = {
    val allMessages = Messages.messages.get("default").getOrElse(Map.empty) ++ Messages.messages.get(lang.code).getOrElse(Map.empty)
    val messages = keys.map(k => (k -> JsString(allMessages(k))))
    JsObject(messages).toString()
  }
}
