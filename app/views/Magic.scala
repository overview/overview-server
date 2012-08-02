package views

case class ScopedMessages(scope: String) {
  def apply(key: String, args: Any*) = {
    play.api.i18n.Messages(scope + "." + key, args : _*)
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
