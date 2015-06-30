package views

import play.api.Play.current
import play.api.libs.json.{JsObject,JsString}
import play.api.i18n.Messages
import scala.annotation.tailrec

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
 * Or, as a shortcut:
 * {{{
 *     @jsMessageKeys = @{Seq("views.DocumentSet._documentSet")}
 *     ...
 * }}}
 * ..., which will expand to all keys with that prefix.
 *
 * 
 * This is wired into `main.scala.html`: just pass the `jsMessageKeys`
 * argument, a `Seq` of `String`s. You can only use one such array per page.
 */
object JsMessages {
  private def parentKey(key: String): Option[String] = {
    val dotIndex = key.lastIndexOf('.')
    if (dotIndex == -1) None else Some(key.substring(0, dotIndex))
  }

  @tailrec
  private def weWantKeyRec(keySet: Set[String], keyParent: Option[String]): Boolean = keyParent match {
    case None => false
    case Some(key) => keySet.contains(key) || weWantKeyRec(keySet, parentKey(key))
  }

  /** true iff key is allowed by keySet. */
  private def weWantKey(keySet: Set[String], key: String): Boolean = {
    keySet.contains(key) || weWantKeyRec(keySet, parentKey(key))
  }

  /** Returns a JSON object with translations for some strings.
    *
    * Not that this is a bottleneck, but it really ought to be memoized
    * somehow.
    */
  def apply(keys: Seq[String])(implicit messages: Messages): String = {
    val keySet = keys.toSet

    val m = messages.messages.messages // Messages -> MessagesApi -> Map. Fun!
    val allMessages = m.get("default").getOrElse(Map.empty) ++ m.get(messages.lang.code).getOrElse(Map.empty)

    val jsMessages: Seq[(String,JsString)] = allMessages
      .filterKeys(weWantKey(keySet, _))
      .mapValues(JsString.apply _)
      .toSeq
    JsObject(jsMessages).toString
  }
}
