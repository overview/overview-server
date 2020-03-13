package modules

import javax.inject.{Inject,Provider}
import play.api.{Configuration,Environment,Mode}
import play.api.inject.{Module}
import controllers.{Assets,AssetsMetadata,AssetsMetadataProvider,AssetsFinder,AssetsFinderProvider,AssetsConfiguration}
import scala.concurrent.blocking

/** AssetsConfiguration provider that assumes Mode.Prod.
  *
  * This undoes Play's default AssetsConfiguration, which treats test and dev
  * differently. We want cache-control: lots, always, because Webpack handles
  * outputting md5s.
  */
case class ConstantAssetsConfigurationProvider @Inject() (env: Environment, conf: Configuration) extends Provider[AssetsConfiguration] {
  def get = {
    AssetsConfiguration.fromConfiguration(conf, Mode.Prod).copy(
      enableCaching = (env.mode != Mode.Dev),
      checkForMinified = (env.mode != Mode.Dev)
    )
  }
}

class ConstantAssetsModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[Assets].toSelf,
    bind[AssetsMetadata].toProvider[AssetsMetadataProvider],
    bind[AssetsFinder].toProvider[AssetsFinderProvider],
    bind[AssetsConfiguration].toProvider[ConstantAssetsConfigurationProvider] // overridden
  )
}
