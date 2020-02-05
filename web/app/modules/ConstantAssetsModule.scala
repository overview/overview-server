package modules

import javax.inject.{Inject,Provider}
import play.api.{Configuration,Environment,Mode}
import play.api.inject.{Module}
import controllers.{Assets,AssetsMetadata,AssetsMetadataProvider,AssetsFinder,AssetsFinderProvider,AssetsConfiguration}

/** AssetsConfiguration provider that assumes Mode.Prod.
  *
  * This undoes Play's default AssetsConfiguration, which treats test and dev
  * differently.
  */
case class ConstantAssetsConfigurationProvider @Inject() (conf: Configuration) extends Provider[AssetsConfiguration] {
  def get = AssetsConfiguration.fromConfiguration(conf, Mode.Prod)
}

/** Register AssetsConfiguration to always behave as though mode is Mode.Prod. */
class ConstantAssetsModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    // These mimic controllers.AssetsModule:
    bind[Assets].toSelf,
    bind[AssetsMetadata].toProvider[AssetsMetadataProvider],
    bind[AssetsFinder].toProvider[AssetsFinderProvider],
    // ... and this one is our override:
    bind[AssetsConfiguration].toProvider[ConstantAssetsConfigurationProvider]
  )
}
