package modules

import javax.inject.{Inject,Provider}
import play.api.{ApplicationLoader,Configuration,Environment,Mode}
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder,GuiceApplicationLoader}
import controllers.AssetsConfiguration

case class PlayModeIndependentAssetsConfigurationProvider @Inject() (conf: Configuration) extends Provider[AssetsConfiguration] {
  def get = AssetsConfiguration.fromConfiguration(conf, Mode.Prod)
}

class CustomApplicationLoader extends GuiceApplicationLoader() {
  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    super.builder(context)
      .overrides(bind[AssetsConfiguration].toProvider[PlayModeIndependentAssetsConfigurationProvider])
  }
}
