package controllers.auth

import com.google.inject.ImplementedBy
import play.api.Play
import javax.inject.{Inject,Singleton}

@ImplementedBy(classOf[DefaultAuthConfig])
trait AuthConfig {
  /** If false, the browser is always logged in as a dummy admin User. */
  val isMultiUser: Boolean

  /** If true, only administrators are allowed to export document sets. */
  val isAdminOnlyExport: Boolean
}

class DefaultAuthConfig @Inject() (
  configuration: play.api.Configuration
) extends AuthConfig {
  override val isMultiUser = configuration.get[Boolean]("overview.multi_user")
  override val isAdminOnlyExport = configuration.get[Boolean]("overview.admin_only_export")
}
