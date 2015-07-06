package controllers.auth

import play.api.Play

private[auth] object AuthConfig {
  val isMultiUser = Play.maybeApplication
    .flatMap(_.configuration.getBoolean("overview.multi_user"))
    .getOrElse(true)
}
