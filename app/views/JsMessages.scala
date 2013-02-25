package views

import play.api.Play.current
import play.api.libs.json.{JsObject,JsString}
import play.api.i18n.{Lang,Messages}

/** Embeds translated messages in a JsObject.
 *
 * Usage:
 * {{{
 *     val jsMessages = JsMessages(Seq(
 *         "views.DocumentSet._documentSet.jobs_to_process",
 *         "views.DocumentSet._documentSet.action_import"
 *     ))
 * }}}
 * now `jsMessages` is (JSON-formatted):
 * {{{
 *     {
 *       "views.DocumentSet._documentSet.jobs_to_process": "foo",
 *       "views.DocumentSet._documentSet.action_import": "bar"
 *     }
 * }}}
 * You can use this to embed strings for client-side translation: strings that
 * wouldn't make sense directly in the HTML. In a Play (Scala HTML) template:
 * {{{
 *     @jsMessageKeys = @{Seq(
 *       "views.DocumentSet._documentSet.jobs_to_process",
 *       "views.DocumentSet._documentSet.action_import"
 *     )}
 *
 *     <script type="text/javascript">
 *       window.messages = @Html(views.JsMessages(jsMessageKeys));
 *     </script>
 * }}}
 * 
 * This is wired into `main.scala.html`: just pass the `jsMessageKeys`
 * argument, a `Seq` of `String`s. You can only use one such array per page.
 */
object JsMessages {
  def apply(keys: Seq[String])(implicit lang: Lang) = {
    val allMessages = Messages.messages.get("default").getOrElse(Map.empty) ++ Messages.messages.get(lang.code).getOrElse(Map.empty)
    val messages = keys.map(k => (k -> JsString(allMessages(k))))
    JsObject(messages).toString()
  }
}
