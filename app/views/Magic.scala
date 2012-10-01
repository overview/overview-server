package views

/**
 * A convenience class on top of Messages.
 *
 * Usage:
 *
 *     val m = ScopedMessages("toplevel.key")
 *     val message = m("subkey")
 *
 * This is equivalent to:
 *
 *     val message = Messages("toplevel.key.subkey")
 */
case class ScopedMessages(scope: String) {
  /**
   * @return a translated message for the given sub-key
   */
  def apply(key: String, args: Any*) : String = {
    play.api.i18n.Messages(scope + "." + key, args : _*)
  }

  /**
   * @return a translated message for the given sub-key, or None if the
   *         key isn't translated.
   */
  def optional(key: String, args: Any*) : Option[String] = {
    val ret = apply(key, args)
    if (ret == scope + "." + key) {
      None
    } else {
      Some(ret)
    }
  }
}

/*
 * Functions that every template can access.
 */

object Magic {
  val t = play.api.i18n.Messages
  val scopedMessages = ScopedMessages

  implicit val fieldConstructor = views.html.helper.twitterBootstrap.twitterBootstrapField
}
