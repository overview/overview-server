package controllers.auth

import play.api.Play

// This construction of flatMap/getOrElse provides an auth default in case:
//  - Play can't get the app
//  - the config is not set
// In both cases we fail to more restrictive permissions
private[auth] object AuthConfig {
  val isMultiUser = Play.maybeApplication
    .flatMap(_.configuration.getBoolean("overview.multi_user"))
    .getOrElse(true)

  val isAdminOnlyExport = Play.maybeApplication
    .flatMap(_.configuration.getBoolean("overview.admin_only_export"))
    .getOrElse(true)  // default to restricted exports if conf not found
}
