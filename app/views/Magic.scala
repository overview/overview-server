package views

case class ScopedMessages(scope: String) {
  def apply(key: String, args: Any*) = {
    play.api.i18n.Messages(scope + "." + key, args : _*)
  }
}

object Magic {
  val t = play.api.i18n.Messages
  val scopedMessages = ScopedMessages

  implicit val fieldConstructor = views.html.helper.twitterBootstrap.twitterBootstrapField
}
